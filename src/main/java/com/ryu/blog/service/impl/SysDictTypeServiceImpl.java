package com.ryu.blog.service.impl;

import com.ryu.blog.dto.SysDictTypeAddDTO;
import com.ryu.blog.dto.SysDictTypeUpdateDTO;
import com.ryu.blog.dto.sysDictTypeQueryDTO;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
public class SysDictTypeServiceImpl implements SysDictTypeService {

    private final SysDictTypeRepository dictTypeRepository;
    private final SysDictItemRepository dictItemRepository;
    private final SysDictTypeMapper sysDictTypeMapper;
    private final R2dbcEntityTemplate entityTemplate;

    /**
     * 基础查询操作
     */
    
    @Override
    public Mono<PageResult<SysDictTypeVO>> getDictTypePage(sysDictTypeQueryDTO queryDTO) {
        log.debug("分页查询字典类型, 查询条件: {}", queryDTO);
        
        Pageable pageable = PageRequest.of(queryDTO.getCurrentPage().intValue() - 1, queryDTO.getPageSize().intValue());
        
        Criteria criteria = Criteria.empty();
        
        // 只查询未删除的记录
        criteria = criteria.and("is_deleted").is(SystemConstants.NOT_DELETED);
        
        // 根据类型名称模糊查询
        if (StringUtils.hasText(queryDTO.getTypeName())) {
            criteria = criteria.and("type_name").like("%" + queryDTO.getTypeName() + "%");
        }
        
        // 根据类型编码精确查询
        if (StringUtils.hasText(queryDTO.getTypeCode())) {
            criteria = criteria.and("dict_type").is(queryDTO.getTypeCode());
        }
        
        Query query = Query.query(criteria);
        
        return entityTemplate.select(SysDictType.class)
                .matching(query.with(pageable))
                .all()
                .map(sysDictTypeMapper::toVO)
                .collectList()
                .zipWith(entityTemplate.count(Query.query(criteria), SysDictType.class))
                .map(tuple -> {
                    List<SysDictTypeVO> content = tuple.getT1();
                    Long total = tuple.getT2();
                    return new PageResult<>(content, total, queryDTO.getPageSize(), queryDTO.getCurrentPage());
                });
    }
    
    @Override
    public Flux<SysDictTypeVO> getAllDictTypes() {
        log.debug("查询所有字典类型");
        return dictTypeRepository.findAllNotDeleted()
                .map(sysDictTypeMapper::toVO);
    }

    @Override
    public Mono<SysDictTypeVO> getDictTypeById(Long id) {
        log.debug("根据ID查询字典类型: {}", id);
        return dictTypeRepository.findByIdAndNotDeleted(id)
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
                    dictType.setIsDeleted(SystemConstants.NOT_DELETED); // 设置为未删除状态
                    // 如果未设置状态，默认为启用
                    if (dictType.getStatus() == null) {
                        dictType.setStatus(SystemConstants.STATUS_NORMAL);
                    }
                    return dictTypeRepository.save(dictType);
                })
                .map(sysDictTypeMapper::toVO);
    }

    @Override
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
    public Mono<Void> deleteDictType(Long id) {
        log.debug("删除字典类型, ID: {}", id);
        
        return dictTypeRepository.findByIdAndNotDeleted(id)
                .flatMap(dictType -> {
                    // 先删除关联的字典项
                    return dictItemRepository.deleteByDictTypeId(id)
                            .then(Mono.defer(() -> {
                                // 逻辑删除字典类型
                                dictType.setIsDeleted(SystemConstants.IS_DELETED);
                                dictType.setUpdateTime(LocalDateTime.now());
                                return dictTypeRepository.save(dictType);
                            }));
                })
                .then();
    }
    
    @Override
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
                                        dictType.setIsDeleted(SystemConstants.IS_DELETED);
                                        dictType.setUpdateTime(LocalDateTime.now());
                                        return dictTypeRepository.save(dictType);
                                    });
                        })))
                .then();
    }
} 