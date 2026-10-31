package com.ryu.blog.service.impl;

import com.ryu.blog.constant.CacheConstants;
import com.ryu.blog.dto.SysDictTypeAddDTO;
import com.ryu.blog.dto.SysDictTypeUpdateDTO;
import com.ryu.blog.dto.SysDictTypeQueryDTO;
import com.ryu.blog.entity.SysDictType;
import com.ryu.blog.mapper.SysDictTypeMapper;
import com.ryu.blog.repository.SysDictItemRepository;
import com.ryu.blog.repository.SysDictTypeRepository;
import com.ryu.blog.service.SysDictTypeService;
import com.ryu.blog.vo.PageResult;
import com.ryu.blog.vo.SysDictTypeVO;
import com.ryu.blog.constant.SystemConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统字典类型服务实现类
 *
 * @author ryu 475118582@qq.com
 */
@Service
@RequiredArgsConstructor
@Slf4j
@CacheConfig(cacheNames = CacheConstants.DICT_TYPE_CACHE)
public class SysDictTypeServiceImpl implements SysDictTypeService {

    private final SysDictTypeRepository dictTypeRepository;
    private final SysDictItemRepository dictItemRepository;
    private final SysDictTypeMapper sysDictTypeMapper;

    /**
     * 基础查询操作
     */
    
    @Override
    public Mono<PageResult<SysDictTypeVO>> getDictTypePage(SysDictTypeQueryDTO queryDTO) {
        log.debug("分页查询字典类型, 查询条件: {}", queryDTO);

        int limit = queryDTO.getPageSize().intValue();
        long offset = (queryDTO.getCurrentPage().intValue() - 1L) * limit;

        // 空串表示不过滤该条件（见 SysDictTypeRepository#findByCondition）
        String typeName = StringUtils.hasText(queryDTO.getTypeName()) ? queryDTO.getTypeName() : "";
        String dictType = StringUtils.hasText(queryDTO.getTypeCode()) ? queryDTO.getTypeCode() : "";

        return dictTypeRepository.findByCondition(typeName, dictType, limit, offset)
                .map(sysDictTypeMapper::toVO)
                .collectList()
                .zipWith(dictTypeRepository.countByCondition(typeName, dictType))
                .map(tuple -> {
                    List<SysDictTypeVO> content = tuple.getT1();
                    Long total = tuple.getT2();
                    return new PageResult<>(content, total, queryDTO.getPageSize(), queryDTO.getCurrentPage());
                });
    }
    
    @Override
    @Cacheable(key = "'" + CacheConstants.DICT_TYPE_ALL_KEY + "'")
    public Flux<SysDictTypeVO> getAllDictTypes() {
        log.debug("查询所有字典类型");
        return dictTypeRepository.findAllNotDeleted()
                .map(sysDictTypeMapper::toVO);
    }

    @Override
    @Cacheable(key = "'" + CacheConstants.DICT_TYPE_BY_ID_KEY + "' + #id", unless = "#result == null")
    public Mono<SysDictTypeVO> getDictTypeById(Long id) {
        log.debug("根据ID查询字典类型: {}", id);
        return dictTypeRepository.findByIdAndNotDeleted(id)
                .map(sysDictTypeMapper::toVO);
    }
    
    @Override
    @Cacheable(key = "'" + CacheConstants.DICT_TYPE_BY_CODE_KEY + "' + #dictType", unless = "#result == null")
    public Mono<SysDictTypeVO> getDictTypeByCode(String dictType) {
        log.debug("根据编码查询字典类型: {}", dictType);
        return dictTypeRepository.findByDictTypeAndNotDeleted(dictType)
                .map(sysDictTypeMapper::toVO);
    }
    
    @Override
    public Mono<SysDictType> getDictTypeEntityById(Long id) {
        log.debug("根据ID查询字典类型实体: {}", id);
        return dictTypeRepository.findByIdAndNotDeleted(id);
    }
    
    /**
     * 数据操作
     */

    @Override
    @Caching(evict = {
            @CacheEvict(key = "'" + CacheConstants.DICT_TYPE_ALL_KEY + "'"),
            // 用入参而不是 #result：响应式场景下 #result 拿到的是返回的 Mono，
            // 取不到解析后的实体的属性，key 会算错、缓存清不掉
            @CacheEvict(key = "'" + CacheConstants.DICT_TYPE_BY_CODE_KEY + "' + #dictTypeDTO.dictType")
    })
    public Mono<SysDictTypeVO> createDictType(SysDictTypeAddDTO dictTypeDTO) {
        log.debug("创建字典类型: {}", dictTypeDTO);
        
        SysDictType dictType = sysDictTypeMapper.toEntity(dictTypeDTO);
        return dictTypeRepository.countByDictTypeAndNotDeleted(dictType.getDictType())
                .flatMap(count -> {
                    if (count > 0) {
                        return Mono.error(new IllegalArgumentException("字典类型编码已存在"));
                    }
                    
                    LocalDateTime now = LocalDateTime.now();
                    dictType.setCreateTime(now);
                    dictType.setUpdateTime(now);
                    dictType.setIsDeleted(false); // 设置为未删除状态
                    // 如果未设置状态，默认为启用
                    if (dictType.getStatus() == null) {
                        dictType.setStatus(SystemConstants.STATUS_NORMAL);
                    }
                    return dictTypeRepository.save(dictType);
                })
                .map(sysDictTypeMapper::toVO);
    }

    @Override
    @Caching(evict = {
            // 编码可以被改掉，而缓存键里存的是编码 —— 用新的编码驱逐只会删掉一个
            // 本来就不存在的键，旧的 code:xxx 会一直留到 TTL 结束。整片清最稳。
            @CacheEvict(allEntries = true),
            // 字典项的缓存键同样含类型编码/类型ID，改类型名或编码后都必须失效
            @CacheEvict(cacheNames = CacheConstants.DICT_ITEM_CACHE, allEntries = true)
    })
    public Mono<SysDictTypeVO> updateDictType(SysDictTypeUpdateDTO dictTypeDTO) {
        log.debug("更新字典类型: {}", dictTypeDTO);
        
        return dictTypeRepository.findByIdAndNotDeleted(dictTypeDTO.getId())
                .flatMap(existingType -> {
                    if (!existingType.getDictType().equals(dictTypeDTO.getDictType())) {
                        return dictTypeRepository.countByDictTypeAndNotDeleted(dictTypeDTO.getDictType())
                                .flatMap(count -> {
                                    if (count > 0) {
                                        return Mono.error(new IllegalArgumentException("字典类型编码已存在"));
                                    }
                                    return updateDictTypeEntity(existingType, dictTypeDTO);
                                });
                    }
                    return updateDictTypeEntity(existingType, dictTypeDTO);
                })
                .switchIfEmpty(Mono.error(new IllegalArgumentException("字典类型不存在")))
                .map(sysDictTypeMapper::toVO);
    }

    private Mono<SysDictType> updateDictTypeEntity(SysDictType existingType, SysDictTypeUpdateDTO dictTypeDTO) {
        sysDictTypeMapper.updateEntity(dictTypeDTO, existingType);
        existingType.setUpdateTime(LocalDateTime.now());
        return dictTypeRepository.save(existingType);
    }

    @Override
    @Transactional
    @Caching(evict = {
            // 删类型会级联逻辑删除它的字典项，两个缓存都要清，
            // 否则 getDictItemsByType 还能把已删类型的项吐出来。
            @CacheEvict(allEntries = true),
            @CacheEvict(cacheNames = CacheConstants.DICT_ITEM_CACHE, allEntries = true)
    })
    public Mono<Void> deleteDictType(Long id) {
        log.debug("删除字典类型, ID: {}", id);
        
        return dictTypeRepository.findByIdAndNotDeleted(id)
                .flatMap(dictType -> {
                    // 先删除关联的字典项
                    return dictItemRepository.deleteByDictTypeId(id)
                            .then(Mono.defer(() -> {
                                // 逻辑删除字典类型
                                dictType.setIsDeleted(true);
                                dictType.setUpdateTime(LocalDateTime.now());
                                return dictTypeRepository.save(dictType);
                            }));
                })
                .then();
    }
    
    @Override
    @Caching(evict = {
            @CacheEvict(allEntries = true),
            @CacheEvict(cacheNames = CacheConstants.DICT_ITEM_CACHE, allEntries = true)
    })
    public Mono<Void> batchDeleteDictTypes(List<String> ids) {
        log.debug("批量删除字典类型, IDs: {}", ids);
        
        if (ids == null || ids.isEmpty()) {
            return Mono.empty();
        }
        
        return Flux.fromIterable(ids)
                .map(Long::valueOf)
                .flatMap(id -> dictItemRepository.deleteByDictTypeId(id)
                        .then(Mono.defer(() -> {
                            // 逻辑删除字典类型
                            return dictTypeRepository.findByIdAndNotDeleted(id)
                                    .flatMap(dictType -> {
                                        dictType.setIsDeleted(true);
                                        dictType.setUpdateTime(LocalDateTime.now());
                                        return dictTypeRepository.save(dictType);
                                    });
                        })))
                .then();
    }
    
    /**
     * 缓存管理
     */
    
    @Override
    @CacheEvict(allEntries = true)
    public Mono<Boolean> clearAllCache() {
        return Mono.fromCallable(() -> {
            log.info("清除所有字典类型缓存");
            return true;
        }).subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    @Caching(evict = {
            @CacheEvict(key = "'" + CacheConstants.DICT_TYPE_BY_CODE_KEY + "' + #dictType"),
            @CacheEvict(key = "'" + CacheConstants.DICT_TYPE_ALL_KEY + "'")
    })
    public Mono<Boolean> refreshCache(String dictType) {
        return Mono.fromCallable(() -> {
            log.info("刷新字典类型缓存: {}", dictType);
            return true;
        }).subscribeOn(Schedulers.boundedElastic());
    }
} 