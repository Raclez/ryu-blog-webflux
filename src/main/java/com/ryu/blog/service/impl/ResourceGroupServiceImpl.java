package com.ryu.blog.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.ryu.blog.constant.CacheConstants;
import com.ryu.blog.dto.ResourceGroupCreateDTO;
import com.ryu.blog.dto.ResourceGroupFileDTO;
import com.ryu.blog.dto.ResourceGroupQueryDTO;
import com.ryu.blog.dto.ResourceGroupUpdateDTO;
import com.ryu.blog.entity.ResourceGroup;
import com.ryu.blog.entity.ResourceGroupFileRel;
import com.ryu.blog.mapper.ResourceGroupMapper;
import com.ryu.blog.repository.ResourceGroupFileRelRepository;
import com.ryu.blog.repository.ResourceGroupRepository;
import com.ryu.blog.repository.UserRepository;
import com.ryu.blog.service.ResourceGroupService;
import com.ryu.blog.utils.JsonUtils;
import com.ryu.blog.utils.SaTokenUtils;
import com.ryu.blog.vo.PageResult;
import com.ryu.blog.vo.ResourceGroupVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 资源组服务实现类
 *
 * @author ryu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceGroupServiceImpl implements ResourceGroupService {

    private final ResourceGroupRepository resourceGroupRepository;
    private final ResourceGroupFileRelRepository resourceGroupFileRelRepository;
    private final UserRepository userRepository;
    private final ResourceGroupMapper resourceGroupMapper;
    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;
    
    // 缓存前缀
    private static final String RESOURCE_GROUP_CACHE_PREFIX = CacheConstants.RESOURCE_GROUP_CACHE_PREFIX;
    // 缓存时间（小时）
    private static final long CACHE_HOURS = 24;

    @Override
    @Transactional
    public Mono<Void> createResourceGroup(ResourceGroupCreateDTO dto, ServerWebExchange exchange) {
        return checkGroupNameExists(dto.getGroupName(), null)
                .filter(exists -> !Boolean.TRUE.equals(exists))
                .switchIfEmpty(Mono.error(new RuntimeException("资源组名称已存在")))
                .flatMap(exists -> SaTokenUtils.exec(exchange, StpUtil::getLoginIdAsLong))
                .flatMap(userId -> {
                    ResourceGroup group = resourceGroupMapper.toEntity(dto);
                    group.setCreatorId(userId);
                    group.setCreateTime(LocalDateTime.now());
                    group.setUpdateTime(LocalDateTime.now());
                    group.setIsDeleted(false);
                    return resourceGroupRepository.save(group);
                })
                .flatMap(savedGroup -> clearResourceGroupCache()
                        .then(Mono.fromRunnable(() -> log.info("资源组创建成功: id={}, name={}", savedGroup.getId(), savedGroup.getGroupName()))))
                .then();
    }

    @Override
    @Transactional
    public Mono<Void> updateResourceGroup(ResourceGroupUpdateDTO dto) {
        return resourceGroupRepository.findById(dto.getId())
                .switchIfEmpty(Mono.error(new RuntimeException("资源组不存在")))
                .flatMap(existingGroup -> {
                    if (dto.getGroupName() != null && !dto.getGroupName().equals(existingGroup.getGroupName())) {
                        return checkGroupNameExists(dto.getGroupName(), dto.getId())
                                .filter(exists -> !Boolean.TRUE.equals(exists))
                                .switchIfEmpty(Mono.error(new RuntimeException("资源组名称已存在")))
                                .flatMap(exists -> updateGroupInternal(dto, existingGroup));
                    }
                    return updateGroupInternal(dto, existingGroup);
                })
                .flatMap(savedGroup -> clearResourceGroupCache()
                        .then(Mono.fromRunnable(() -> log.info("资源组更新成功: id={}, name={}", savedGroup.getId(), savedGroup.getGroupName()))))
                .then();
    }
    
    /**
     * 内部更新资源组方法
     */
    private Mono<ResourceGroup> updateGroupInternal(ResourceGroupUpdateDTO dto, ResourceGroup existingGroup) {
        resourceGroupMapper.updateEntityFromDTO(dto, existingGroup);
        existingGroup.setUpdateTime(LocalDateTime.now());
        return resourceGroupRepository.save(existingGroup);
    }

    @Override
    @Transactional
    public Mono<Void> deleteResourceGroup(Long id) {
        return resourceGroupRepository.findById(id)
                .switchIfEmpty(Mono.error(new RuntimeException("资源组不存在")))
                .flatMap(group ->
                    resourceGroupFileRelRepository.deleteByGroupId(id)
                            .then(Mono.defer(() -> {
                                group.setIsDeleted(true);
                                group.setUpdateTime(LocalDateTime.now());
                                return resourceGroupRepository.save(group);
                            }))
                            .flatMap(savedGroup -> clearResourceGroupCache()
                                    .then(Mono.fromRunnable(() -> log.info("资源组删除成功: id={}", id))))
                )
                .then();
    }

    @Override
    public Flux<ResourceGroupVO> getUserResourceGroups(Long userId) {
        // 从缓存获取
        String cacheKey = RESOURCE_GROUP_CACHE_PREFIX + "user:" + userId;
        
        return reactiveRedisTemplate.opsForValue().get(cacheKey)
                .flatMap(jsonStr -> {
                    try {
                        List<ResourceGroupVO> vos = JsonUtils.deserializeList(jsonStr, ResourceGroupVO.class);
                        log.debug("从缓存获取用户资源组列表: {}", userId);
                        return Mono.justOrEmpty(vos);
                    } catch (Exception e) {
                        log.error("解析用户资源组列表JSON数据失败: {}", e.getMessage(), e);
                        return Mono.empty();
                    }
                })
                .flatMapMany(Flux::fromIterable)
                .switchIfEmpty(
                    resourceGroupRepository.findByCreatorIdAndIsDeleted(userId, 0)
                        .map(resourceGroupMapper::toVO)
                        .collectList()
                        .flatMap(vos -> {
                            // 缓存列表
                            if (vos.isEmpty()) {
                                return Mono.just(vos);
                            }
                            
                            try {
                                String json = JsonUtils.serialize(vos);
                                return reactiveRedisTemplate.opsForValue()
                                        .set(cacheKey, json, Duration.ofHours(CACHE_HOURS))
                                        .thenReturn(vos);
                            } catch (Exception e) {
                                log.error("序列化用户资源组列表失败: {}", e.getMessage(), e);
                                return Mono.just(vos);
                            }
                        })
                        .flatMapMany(Flux::fromIterable)
                );
    }

    @Override
    @Transactional
    public Mono<Void> addFilesToGroup(ResourceGroupFileDTO dto) {
        return resourceGroupRepository.findByIdAndIsDeleted(dto.getGroupId(), 0)
                .switchIfEmpty(Mono.error(new RuntimeException("资源组不存在或已删除")))
                .flatMap(group ->
                            Flux.fromIterable(dto.getFileIds())
                                .flatMap(fileId ->
                                    resourceGroupFileRelRepository.findByGroupIdAndFileId(dto.getGroupId(), fileId)
                                        .hasElement()
                                        .flatMap(exists -> {
                                            if (Boolean.TRUE.equals(exists)) {
                                                return Mono.empty();
                                            }
                                            ResourceGroupFileRel rel = ResourceGroupFileRel.builder()
                                                    .groupId(dto.getGroupId())
                                                    .fileId(fileId)
                                                    .createTime(LocalDateTime.now())
                                                    .build();
                                            return resourceGroupFileRelRepository.save(rel);
                                        })
                                )
                                .then()
                )
                .flatMap(v -> clearResourceGroupCache()
                        .then(Mono.fromRunnable(() -> log.info("文件添加到资源组成功: groupId={}, fileCount={}",
                                dto.getGroupId(), dto.getFileIds().size()))))
                .then();
    }

    @Override
    @Transactional
    public Mono<Void> removeFilesFromGroup(ResourceGroupFileDTO dto) {
        return resourceGroupRepository.findByIdAndIsDeleted(dto.getGroupId(), 0)
                .switchIfEmpty(Mono.error(new RuntimeException("资源组不存在或已删除")))
                .flatMap(group ->
                    Flux.fromIterable(dto.getFileIds())
                        .flatMap(fileId ->
                            resourceGroupFileRelRepository.deleteByGroupIdAndFileId(dto.getGroupId(), fileId)
                        )
                        .then()
                )
                .flatMap(v -> clearResourceGroupCache()
                        .then(Mono.fromRunnable(() -> log.info("从资源组移除文件成功: groupId={}, fileCount={}",
                                dto.getGroupId(), dto.getFileIds().size()))))
                .then();
    }

    @Override
    public Mono<PageResult<Long>> getGroupFileIds(ResourceGroupQueryDTO dto) {
        Long groupId = dto.getGroupId();
        Long currentPage = dto.getCurrentPage();
        Long pageSize = dto.getPageSize();
        
        // 计算分页参数
        int page = Math.max(0, currentPage.intValue() - 1); // 从0开始
        int size = pageSize.intValue();
        long offset = (long) page * size;
        
        // 根据是否指定groupId决定查询方式
        if (groupId == null) {
            // 查询所有文件
            return resourceGroupFileRelRepository.countAllFiles()
                    .flatMap(total -> {
                        if (total == 0) {
                            return Mono.just(new PageResult<Long>());
                        }
                        
                        // 查询所有文件ID列表
                        return resourceGroupFileRelRepository.findAllFileIds(size, offset)
                                .collectList()
                                .map(fileIds -> {
                                    PageResult<Long> result = new PageResult<>();
                                    result.setRecords(fileIds);
                                    result.setTotal(total);
                                    result.setSize(size);
                                    result.setCurrent(currentPage);
                                    result.setPages((total + size - 1) / size); // 计算总页数
                                    return result;
                                });
                    });
        } else {
            // 按资源组ID查询
            return resourceGroupRepository.findByIdAndIsDeleted(groupId, 0)
                    .switchIfEmpty(Mono.error(new RuntimeException("资源组不存在或已删除")))
                    .flatMap(group -> {
                        // 查询总文件数
                        return resourceGroupFileRelRepository.countByGroupId(groupId)
                                .flatMap(total -> {
                                    if (total == 0) {
                                        return Mono.just(new PageResult<Long>());
                                    }
                                    
                                    // 查询文件ID列表
                                    return resourceGroupFileRelRepository.findFileIdsByGroupId(groupId, size, offset)
                                            .collectList()
                                            .map(fileIds -> {
                                                PageResult<Long> result = new PageResult<>();
                                                result.setRecords(fileIds);
                                                result.setTotal(total);
                                                result.setSize(size);
                                                result.setCurrent(currentPage);
                                                result.setPages((total + size - 1) / size); // 计算总页数
                                                return result;
                                            });
                                });
                    });
        }
    }

    @Override
    public Mono<Boolean> checkGroupNameExists(String groupName, Long excludeId) {
        if (excludeId == null) {
            // 创建时检查
            return resourceGroupRepository.findByGroupNameAndIsDeleted(groupName, 0)
                    .map(group -> true)
                    .defaultIfEmpty(false);
        } else {
            // 更新时检查（排除自身）
            return resourceGroupRepository.findByGroupNameAndIsDeleted(groupName, 0)
                    .map(group -> !group.getId().equals(excludeId))
                    .defaultIfEmpty(false);
        }
    }

    @Override
    public Flux<ResourceGroupVO> getFileResourceGroups(Long fileId) {
        // 从缓存获取
        String cacheKey = RESOURCE_GROUP_CACHE_PREFIX + "file:" + fileId;
        
        return reactiveRedisTemplate.opsForValue().get(cacheKey)
                .flatMap(jsonStr -> {
                    try {
                        List<ResourceGroupVO> vos = JsonUtils.deserializeList(jsonStr, ResourceGroupVO.class);
                        log.debug("从缓存获取文件所属资源组列表: {}", fileId);
                        return Mono.justOrEmpty(vos);
                    } catch (Exception e) {
                        log.error("解析文件所属资源组列表JSON数据失败: {}", e.getMessage(), e);
                        return Mono.empty();
                    }
                })
                .flatMapMany(Flux::fromIterable)
                .switchIfEmpty(
                    resourceGroupFileRelRepository.findByFileId(fileId)
                        .flatMap(rel -> 
                            resourceGroupRepository.findByIdAndIsDeleted(rel.getGroupId(), 0)
                        )
                        .map(resourceGroupMapper::toVO)
                        .collectList()
                        .flatMap(vos -> {
                            // 缓存列表
                            if (vos.isEmpty()) {
                                return Mono.just(vos);
                            }
                            
                            try {
                                String json = JsonUtils.serialize(vos);
                                return reactiveRedisTemplate.opsForValue()
                                        .set(cacheKey, json, Duration.ofHours(CACHE_HOURS))
                                        .thenReturn(vos);
                            } catch (Exception e) {
                                log.error("序列化文件所属资源组列表失败: {}", e.getMessage(), e);
                                return Mono.just(vos);
                            }
                        })
                        .flatMapMany(Flux::fromIterable)
                );
    }
    
    /**
     * 清除资源组缓存
     */
    private Mono<Void> clearResourceGroupCache() {
        log.debug("清除资源组缓存");
        return reactiveRedisTemplate.scan(ScanOptions.scanOptions()
                .match(RESOURCE_GROUP_CACHE_PREFIX + "*").count(100).build())
                .flatMap(key -> reactiveRedisTemplate.delete(key).then())
                .then()
                .doOnError(e -> log.error("清除资源组缓存失败: error={}", e.getMessage()));
    }
} 