package com.ryu.blog.service.impl;

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
import cn.dev33.satoken.stp.StpUtil;
import com.ryu.blog.vo.PageResult;
import com.ryu.blog.vo.ResourceGroupVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.web.server.ServerWebExchange;

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
        // 检查组名是否已存在
        return checkGroupNameExists(dto.getGroupName(), null)
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return Mono.error(new RuntimeException("资源组名称已存在"));
                    }
                    
                    // 获取当前用户ID - 使用响应式方法
                    return SaTokenUtils.exec(exchange, StpUtil::getLoginIdAsLong)
                            .flatMap(userId -> {
                                // 将DTO转换为实体
                                ResourceGroup group = resourceGroupMapper.toEntity(dto);
                                
                                // 设置默认值
                                group.setCreatorId(userId);
                                group.setCreateTime(LocalDateTime.now());
                                group.setUpdateTime(LocalDateTime.now());
                                group.setIsDeleted(0); // 正常状态
                                
                                // 保存实体
                                return resourceGroupRepository.save(group)
                                        .doOnSuccess(savedGroup -> {
                                            // 清除缓存
                                            clearResourceGroupCache();
                                            log.info("资源组创建成功: id={}, name={}", savedGroup.getId(), savedGroup.getGroupName());
                                        })
                                        .then();
                            });
                });
    }

    @Override
    @Transactional
    public Mono<Void> updateResourceGroup(ResourceGroupUpdateDTO dto) {
        return resourceGroupRepository.findById(dto.getId())
                .switchIfEmpty(Mono.error(new RuntimeException("资源组不存在")))
                .flatMap(existingGroup -> {
                    // 如果组名有变化，需要检查是否已存在
                    if (dto.getGroupName() != null && !dto.getGroupName().equals(existingGroup.getGroupName())) {
                        return checkGroupNameExists(dto.getGroupName(), dto.getId())
                                .flatMap(exists -> {
                                    if (Boolean.TRUE.equals(exists)) {
                                        return Mono.error(new RuntimeException("资源组名称已存在"));
                                    }
                                    
                                    return updateGroupInternal(dto, existingGroup);
                                });
                    } else {
                        return updateGroupInternal(dto, existingGroup);
                    }
                });
    }
    
    /**
     * 内部更新资源组方法
     */
    private Mono<Void> updateGroupInternal(ResourceGroupUpdateDTO dto, ResourceGroup existingGroup) {
        // 使用MapStruct更新实体属性
        resourceGroupMapper.updateEntityFromDTO(dto, existingGroup);
        existingGroup.setUpdateTime(LocalDateTime.now());
        
        // 保存更新后的实体
        return resourceGroupRepository.save(existingGroup)
                .doOnSuccess(savedGroup -> {
                    // 清除缓存
                    clearResourceGroupCache();
                    log.info("资源组更新成功: id={}, name={}", savedGroup.getId(), savedGroup.getGroupName());
                })
                .then();
    }

    @Override
    @Transactional
    public Mono<Void> deleteResourceGroup(Long id) {
        return resourceGroupRepository.findById(id)
                .switchIfEmpty(Mono.error(new RuntimeException("资源组不存在")))
                .flatMap(group -> {
                    // 删除资源组与文件的关联
                    return resourceGroupFileRelRepository.deleteByGroupId(id)
                            .then(Mono.defer(() -> {
                                // 逻辑删除资源组（将isDeleted设为1）
                                group.setIsDeleted(1);
                                group.setUpdateTime(LocalDateTime.now());
                                return resourceGroupRepository.save(group);
                            }))
                            .doOnSuccess(savedGroup -> {
                                // 清除缓存
                                clearResourceGroupCache();
                                log.info("资源组删除成功: id={}", id);
                            })
                            .then();
                });
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

                            // 处理每个文件ID
                            Flux.fromIterable(dto.getFileIds())
                                .flatMap(fileId -> 
                                    // 检查关联是否已存在
                                    resourceGroupFileRelRepository.findByGroupIdAndFileId(dto.getGroupId(), fileId)
                                        .hasElement()
                                        .flatMap(exists -> {
                                            if (Boolean.TRUE.equals(exists)) {
                                                // 已存在，跳过
                                                return Mono.empty();
                                            }
                                            
                                            // 创建新的关联
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
                .doOnSuccess(v -> {
                    // 清除相关缓存
                    clearResourceGroupCache();
                    log.info("文件添加到资源组成功: groupId={}, fileCount={}", 
                            dto.getGroupId(), dto.getFileIds().size());
                });
    }

    @Override
    @Transactional
    public Mono<Void> removeFilesFromGroup(ResourceGroupFileDTO dto) {
        return resourceGroupRepository.findByIdAndIsDeleted(dto.getGroupId(), 0)
                .switchIfEmpty(Mono.error(new RuntimeException("资源组不存在或已删除")))
                .flatMap(group -> 
                    // 处理每个文件ID
                    Flux.fromIterable(dto.getFileIds())
                        .flatMap(fileId -> 
                            resourceGroupFileRelRepository.deleteByGroupIdAndFileId(dto.getGroupId(), fileId)
                        )
                        .then()
                )
                .doOnSuccess(v -> {
                    // 清除相关缓存
                    clearResourceGroupCache();
                    log.info("从资源组移除文件成功: groupId={}, fileCount={}", 
                            dto.getGroupId(), dto.getFileIds().size());
                });
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
    private void clearResourceGroupCache() {
        log.debug("清除资源组缓存");
        // 使用通配符清除所有相关缓存
        reactiveRedisTemplate.scan(ScanOptions.scanOptions()
                .match(RESOURCE_GROUP_CACHE_PREFIX + "*").build())
                .flatMap(reactiveRedisTemplate::delete)
                .subscribe();
    }
} 