package com.ryu.blog.service.impl;

import com.ryu.blog.dto.OperatorContext;
import com.ryu.blog.dto.PermissionsAddDTO;
import com.ryu.blog.dto.PermissionsQueryDTO;
import com.ryu.blog.dto.PermissionsUpdateDTO;
import com.ryu.blog.entity.Permissions;
import com.ryu.blog.exception.BusinessException;
import com.ryu.blog.repository.PermissionsRepository;
import com.ryu.blog.repository.RolePermissionRepository;
import com.ryu.blog.repository.UserRoleRepository;
import com.ryu.blog.service.PermissionsService;
import com.ryu.blog.utils.OperatorContexts;
import com.ryu.blog.vo.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 权限服务实现类
 *
 * <p><b>权限判定的口径统一到 {@link UserPermissionResolver}</b>：
 * 原先这里有两条独立的判定路径（{@code getUserPermissionIdentities} 与
 * {@code hasPermission}），两条都<b>不校验角色是否启用</b> ——
 * 于是「禁用角色」之后该角色的权限仍然全部生效。
 *
 * @author ryu 475118582@qq.com
 * @since 1.0.0 2024-08-10
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionsServiceImpl implements PermissionsService {

    private final PermissionsRepository permissionsRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserPermissionResolver permissionResolver;
    private final UserPermissionCache permissionCache;
    /**
     * 用于把权限列表的过滤/分页下推到数据库（Criteria 查询）。
     */
    private final R2dbcEntityTemplate template;

    @Override
    public Flux<Permissions> list() {
        return permissionsRepository.findByIsDeletedOrderByIdAsc(0);
    }

    @Override
    @Transactional
    public Mono<Void> updatePermission(PermissionsUpdateDTO permissionsUpdateDTO) {
        log.info("更新权限: id={}", permissionsUpdateDTO.getId());

        return permissionsRepository.findById(permissionsUpdateDTO.getId())
                .switchIfEmpty(Mono.error(BusinessException.permissionNotExists()))
                .flatMap(permission -> {
                    String newIdentity = permissionsUpdateDTO.getIdentity();
                    if (newIdentity == null || newIdentity.equals(permission.getIdentity())) {
                        return Mono.just(permission);
                    }
                    // 权限标识必须唯一 —— 它是 @SaCheckPermission 的比对依据，
                    // 重复的标识会让鉴权结果不可预期
                    return permissionsRepository.findByIdentity(newIdentity)
                            .filter(existing -> !existing.getId().equals(permission.getId()))
                            .flatMap(existing -> Mono.<Permissions>error(
                                    new IllegalArgumentException("权限标识已存在")))
                            .defaultIfEmpty(permission);
                })
                .flatMap(permission -> {
                    if (permissionsUpdateDTO.getName() != null) {
                        permission.setName(permissionsUpdateDTO.getName());
                    }
                    if (permissionsUpdateDTO.getIdentity() != null) {
                        permission.setIdentity(permissionsUpdateDTO.getIdentity());
                    }
                    if (permissionsUpdateDTO.getDescription() != null) {
                        permission.setDescription(permissionsUpdateDTO.getDescription());
                    }
                    if (permissionsUpdateDTO.getIsActive() != null) {
                        permission.setIsActive(permissionsUpdateDTO.getIsActive());
                    }
                    permission.setUpdateTime(LocalDateTime.now());
                    return permissionsRepository.save(permission);
                })
                // 权限标识改了 → 持有该权限的用户快照必须重建
                .then(refreshCacheOfPermission(permissionsUpdateDTO.getId()));
    }

    @Override
    public Flux<Permissions> getPermissionsByModule(String modulePrefix) {
        return permissionsRepository.findByIdentityStartingWithAndIsDeletedOrderByIdAsc(modulePrefix + ":", 0);
    }

    /**
     * 分页查询权限。
     *
     * <p><b>过滤下推到数据库</b>：原先按名称/标识模糊查询时是
     * 「把整张 {@code t_permissions} 查出来 → 在内存里 filter → 再在内存里分页」，
     * 数据量一大就是全表扫描 + 全量传输。
     * 现在改成用 {@link Criteria} 拼 {@code WHERE}，让数据库做过滤和分页。
     *
     * <p>没有 {@code @Transactional}：计数与取页是两条独立的只读查询，
     * 各自从连接池取连接（见 CLAUDE.md 的事务边界约定）。
     */
    @Override
    public Mono<PageResult<Permissions>> getPermissionsByPage(PermissionsQueryDTO permissionsQuery) {
        long currentPage = permissionsQuery.getCurrentPage() != null ? permissionsQuery.getCurrentPage() : 1;
        long pageSize = permissionsQuery.getPageSize() != null ? permissionsQuery.getPageSize() : 10;

        int page = (int) Math.max(0, currentPage - 1);
        int size = (int) pageSize;

        Mono<Long> total = template.select(Permissions.class)
                .from("t_permissions")
                .matching(Query.query(buildSearchCriteria(permissionsQuery)))
                .count();

        Query pageQuery = Query.query(buildSearchCriteria(permissionsQuery))
                .sort(Sort.by(Sort.Direction.ASC, "id"))
                .limit(size)
                .offset((long) page * size);

        Flux<Permissions> records = template.select(Permissions.class)
                .from("t_permissions")
                .matching(pageQuery)
                .all();

        return total.zipWith(records.collectList())
                .map(tuple -> buildPage(tuple.getT2(), tuple.getT1(), pageSize, currentPage));
    }

    /**
     * 构建权限列表的查询条件。
     *
     * <p>语义与改造前保持一致：
     * <ul>
     *   <li>带 {@code modulePrefix} 时只按前缀过滤（忽略 name/identity）；</li>
     *   <li>否则 name / identity 各自模糊匹配（两个都传时取交集）；</li>
     *   <li>都不传就是全部未删除的权限。</li>
     * </ul>
     */
    private Criteria buildSearchCriteria(PermissionsQueryDTO query) {
        Criteria criteria = Criteria.where("is_deleted").is(0);

        String modulePrefix = query.getModulePrefix();
        if (modulePrefix != null && !modulePrefix.isEmpty()) {
            return criteria.and(Criteria.where("identity").like(modulePrefix + ":%"));
        }

        String name = query.getName();
        if (name != null && !name.isEmpty()) {
            criteria = criteria.and(Criteria.where("name").like("%" + name + "%"));
        }

        String identity = query.getIdentity();
        if (identity != null && !identity.isEmpty()) {
            criteria = criteria.and(Criteria.where("identity").like("%" + identity + "%"));
        }

        return criteria;
    }

    private PageResult<Permissions> buildPage(List<Permissions> records, Long total, long pageSize, long currentPage) {
        PageResult<Permissions> pageResult = new PageResult<>();
        long count = total == null ? 0L : total;
        pageResult.setRecords(records);
        pageResult.setTotal(count);
        pageResult.setSize(pageSize);
        pageResult.setCurrent(currentPage);
        pageResult.setPages(pageSize <= 0 ? 0 : (count + pageSize - 1) / pageSize);
        return pageResult;
    }

    @Override
    @Transactional
    public Mono<Void> savePermission(PermissionsAddDTO permissionsAddDTO) {
        log.info("保存权限: identity={}", permissionsAddDTO.getIdentity());

        Permissions permission = new Permissions();
        permission.setName(permissionsAddDTO.getName());
        permission.setIdentity(permissionsAddDTO.getIdentity());
        permission.setDescription(permissionsAddDTO.getDescription());
        permission.setIsActive(true);
        permission.setIsDeleted(false);

        LocalDateTime now = LocalDateTime.now();
        permission.setCreateTime(now);
        permission.setUpdateTime(now);

        return permissionsRepository.findByIdentity(permissionsAddDTO.getIdentity())
                .hasElement()
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new IllegalArgumentException("权限标识已存在"));
                    }
                    return permissionsRepository.save(permission).then();
                });
    }

    @Override
    @Transactional
    public Mono<Void> removeById(Long id) {
        log.info("删除权限, id: {}", id);

        return permissionsRepository.findById(id)
                .switchIfEmpty(Mono.error(BusinessException.permissionNotExists()))
                .flatMap(permission -> {
                    // <b>先记下受影响的角色与用户</b>：下面紧接着就删关联行了，
                    // 删完再问「谁持有这个权限」只会得到空集 —— 于是没人被刷新、
                    // 权限版本号不自增，旧快照继续有效到 Redis TTL 结束，
                    // <b>等于撤销没生效</b>，而调用方照样拿到 200。
                    return rolePermissionRepository.findRoleIdsByPermissionId(id)
                            .collectList()
                            .flatMap(affectedRoleIds -> Flux.fromIterable(affectedRoleIds)
                                    .concatMap(roleId -> userRoleRepository.findUserIdsByRoleId(roleId))
                                    .distinct()
                                    .collectList()
                                    .flatMap(affectedUserIds -> {
                                        // 逻辑删除
                                        permission.setIsDeleted(true);
                                        permission.setUpdateTime(LocalDateTime.now());

                                        return permissionsRepository.save(permission)
                                                // 删除角色权限关联
                                                // ⚠️ 必须用 concatMap（串行），不能用 flatMap（并发）。
                                                // 本方法带 @Transactional，整条链绑定在同一条 R2DBC 连接上；
                                                // 而 r2dbc-mysql 1.4.0 每条连接只有一个 SPSC 的 RequestQueue，
                                                // 且 submit() 时不加锁 —— 并发提交会误判队满，
                                                // 抛 IllegalStateException: Request queue is full。
                                                .thenMany(Flux.fromIterable(affectedRoleIds))
                                                .concatMap(roleId -> rolePermissionRepository
                                                        .deleteByRoleIdAndPermissionIdIn(roleId, List.of(id)))
                                                .then(refreshPermissionsOfUsers(affectedUserIds));
                                    }));
                });
    }

    @Override
    public Mono<Set<String>> getUserPermissionIdentities(Long userId) {
        return Mono.deferContextual(cv -> {
            OperatorContext ctx = OperatorContexts.from(cv);
            // 归属校验：只能查自己的权限，或持有 system:user:manage
            OwnershipPolicy.requireCanActOn(ctx, userId);
            return permissionResolver.resolvePermissionIdentities(userId);
        });
    }

    @Override
    public Mono<List<String>> getAllModules() {
        return permissionsRepository.findByIsDeletedOrderByIdAsc(0)
                .map(permission -> {
                    String identity = permission.getIdentity();
                    if (identity == null) {
                        return "common";
                    }
                    int index = identity.indexOf(":");
                    return index > 0 ? identity.substring(0, index) : "common";
                })
                .distinct()
                .collectList();
    }

    @Override
    public Mono<Boolean> hasPermission(Long userId, String permissionIdentity) {
        return Mono.deferContextual(cv -> {
            OperatorContext ctx = OperatorContexts.from(cv);
            // 归属校验：只能查自己的权限，或持有 system:user:manage
            OwnershipPolicy.requireCanActOn(ctx, userId);
            return permissionResolver.hasPermission(userId, permissionIdentity);
        });
    }

    @Override
    public Flux<Permissions> getPermissionsByRoleId(Long roleId) {
        return rolePermissionRepository.findPermissionIdsByRoleId(roleId)
                .collectList()
                .flatMapMany(permissionIds -> {
                    if (permissionIds.isEmpty()) {
                        return Flux.empty();
                    }
                    return permissionsRepository.findByIdIn(permissionIds);
                });
    }

    @Override
    @Transactional
    public Mono<Boolean> updatePermissionStatus(List<Long> ids, Boolean isActive) {
        log.info("批量更新权限状态, ids: {}, isActive: {}", ids, isActive);

        if (ids == null || ids.isEmpty()) {
            return Mono.just(false);
        }

        return permissionsRepository.findByIdIn(ids)
                // ⚠️ 必须用 concatMap（串行），不能用 flatMap（并发）。
                // 本方法带 @Transactional，所有查询共用同一条 R2DBC 连接；
                // r2dbc-mysql 的 RequestQueue 是 SPSC 队列且 submit() 无锁，
                // 并发提交会抛 `Request queue is full`，整批更新静默失败。
                .concatMap(permission -> {
                    permission.setIsActive(isActive);
                    permission.setUpdateTime(LocalDateTime.now());
                    return permissionsRepository.save(permission);
                })
                .then()
                .then(Flux.fromIterable(ids).concatMap(this::refreshCacheOfPermission).then())
                .thenReturn(true);
    }

    /**
     * 权限变更后，重建持有该权限的用户的快照
     *
     * <p>受影响的用户 = 持有「含该权限的角色」的所有用户。
     */
    private Mono<Void> refreshCacheOfPermission(Long permissionId) {
        return rolePermissionRepository.findRoleIdsByPermissionId(permissionId)
                .collectList()
                .flatMapMany(Flux::fromIterable)
                .concatMap(roleId -> userRoleRepository.findUserIdsByRoleId(roleId))
                .distinct()
                .collectList()
                .flatMap(affectedUserIds -> refreshPermissionsOfUsers(affectedUserIds));
    }

    /**
     * 重建一批用户的权限快照。
     *
     * <p><b>用于「关联行已经被删掉」的场景</b>（例如删除权限）：
     * 那时已经查不出「谁持有这个权限」了，必须在删之前把名单记下来。
     */
    private Mono<Void> refreshPermissionsOfUsers(List<Long> userIds) {
        return Flux.fromIterable(userIds)
                .distinct()
                .concatMap(userId -> permissionCache.refresh(userId))
                .then();
    }
}
