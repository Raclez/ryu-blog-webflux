package com.ryu.blog.service.impl;

import com.ryu.blog.constant.SystemConstants;
import com.ryu.blog.dto.SysDictItemDTO;
import com.ryu.blog.dto.SysDictItemSaveDTO;
import com.ryu.blog.dto.SysDictItemUpdateDTO;
import com.ryu.blog.entity.SysDictItem;
import com.ryu.blog.entity.SysDictType;
import com.ryu.blog.mapper.SysDictItemMapper;
import com.ryu.blog.repository.SysDictItemRepository;
import com.ryu.blog.repository.SysDictTypeRepository;
import com.ryu.blog.service.SysDictItemService;
import com.ryu.blog.service.SysDictTypeService;
import com.ryu.blog.vo.PageResult;
import com.ryu.blog.vo.SysDictItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统字典项服务实现类
 *
 * @author ryu 475118582@qq.com
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SysDictItemServiceImpl implements SysDictItemService {

    private final SysDictItemRepository dictItemRepository;
    private final SysDictTypeRepository dictTypeRepository;
    private final SysDictTypeService dictTypeService;
    private final SysDictItemMapper sysDictItemMapper;

    /**
     * 基础查询方法
     */

    @Override
    public Mono<SysDictItemVO> getDictItemById(Long id) {
        log.debug("根据ID查询字典项: {}", id);
        return dictItemRepository.findByIdAndNotDeleted(id)
                .flatMap(this::enrichDictItemWithType);
    }
    
    @Override
    public Mono<SysDictItem> getDictItemEntityById(Long id) {
        log.debug("根据ID查询字典项实体: {}", id);
        return dictItemRepository.findByIdAndNotDeleted(id);
    }

    @Override
    public Mono<PageResult<SysDictItemVO>> getDictItemPage(SysDictItemDTO dictItemDTO) {
        log.debug("分页查询字典项, 条件: {}", dictItemDTO);
        // 构建分页参数
        int page = Math.max(0, dictItemDTO.getCurrentPage().intValue() - 1); // Spring Data页码从0开始
        int size = dictItemDTO.getPageSize().intValue();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "sort"));
        
        // 根据字典类型编码查询
        if (StringUtils.hasText(dictItemDTO.getDictType())) {
            return dictTypeRepository.findByDictType(dictItemDTO.getDictType())
                    .flatMap(dictType -> {
                        Long dictTypeId = dictType.getId();
                        Mono<List<SysDictItemVO>> itemsListMono;
                        Mono<Long> countMono;
                        
                        if (StringUtils.hasText(dictItemDTO.getItemLabel())) {
                            // 按字典类型ID和标签查询
                            itemsListMono = dictItemRepository.findByDictTypeIdAndDictItemValueLike(
                                    dictTypeId, dictItemDTO.getItemLabel(), pageable)
                                    .flatMap(item -> enrichWithDictType(item, dictType))
                                    .collectList();
                            countMono = dictItemRepository.countByDictTypeIdAndDictItemValueLike(
                                    dictTypeId, dictItemDTO.getItemLabel());
                        } else {
                            // 只按字典类型ID查询
                            itemsListMono = dictItemRepository.findByDictTypeId(dictTypeId, pageable)
                                    .flatMap(item -> enrichWithDictType(item, dictType))
                                    .collectList();
                            countMono = dictItemRepository.countByDictTypeId(dictTypeId);
                        }
                        
                        // 组合查询结果
                        return Mono.zip(itemsListMono, countMono)
                                .map(tuple -> createPageResult(tuple.getT1(), tuple.getT2(), dictItemDTO));
                    })
                    .switchIfEmpty(Mono.just(new PageResult<>()));
        } else if (StringUtils.hasText(dictItemDTO.getItemLabel())) {
            // 只按标签查询
            Mono<List<SysDictItemVO>> itemsListMono = dictItemRepository.findByDictItemValueLike(
                    dictItemDTO.getItemLabel(), pageable)
                    .flatMap(this::enrichDictItemWithType)
                    .collectList();
            Mono<Long> countMono = dictItemRepository.countByDictItemValueLike(dictItemDTO.getItemLabel());
            
            return Mono.zip(itemsListMono, countMono)
                    .map(tuple -> createPageResult(tuple.getT1(), tuple.getT2(), dictItemDTO));
        } else {
            // 无条件查询
            Mono<List<SysDictItemVO>> itemsListMono = dictItemRepository.findAll(pageable.getSort())
                    .skip((long) page * size)
                    .take(size)
                    .flatMap(this::enrichDictItemWithType)
                    .collectList();
            Mono<Long> countMono = dictItemRepository.count();
            
            return Mono.zip(itemsListMono, countMono)
                    .map(tuple -> createPageResult(tuple.getT1(), tuple.getT2(), dictItemDTO));
        }
    }

    /**
     * 数据操作方法
     */
    
    @Override
    @Transactional
    public Mono<SysDictItemVO> createDictItem(SysDictItemSaveDTO saveDTO) {
        log.debug("创建字典项: {}", saveDTO);
        SysDictItem dictItem = sysDictItemMapper.toEntity(saveDTO);
        
        // 检查字典类型ID是否存在
        return dictTypeService.getDictTypeEntityById(dictItem.getDictTypeId())
                .flatMap(dictType -> dictItemRepository.countByDictTypeIdAndDictItemKeyAndNotDeleted(
                        dictItem.getDictTypeId(), dictItem.getDictItemKey())
                        .flatMap(count -> {
                            if (count > 0) {
                                return Mono.error(new IllegalArgumentException("字典项键已存在"));
                            }
                            
                            LocalDateTime now = LocalDateTime.now();
                            dictItem.setCreateTime(now);
                            dictItem.setUpdateTime(now);
                            dictItem.setIsDeleted(SystemConstants.NOT_DELETED); // 设置为未删除状态
                            // 如果未设置状态，默认为启用
                            if (dictItem.getStatus() == null) {
                                dictItem.setStatus(SystemConstants.STATUS_NORMAL);
                            }
                            return dictItemRepository.save(dictItem)
                                    .flatMap(savedItem -> enrichWithDictType(savedItem, dictType));
                        }))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("字典类型不存在")));
    }

    @Override
    @Transactional
    public Mono<SysDictItemVO> updateDictItem(SysDictItemUpdateDTO dictItemDTO) {
        log.debug("更新字典项: {}", dictItemDTO);
        if (dictItemDTO.getId() == null) {
            return Mono.error(new IllegalArgumentException("字典项ID不能为空"));
        }
        
        return dictItemRepository.findByIdAndNotDeleted(dictItemDTO.getId())
                .flatMap(existingItem -> {
                    // 检查键是否变更，如果变更需要检查唯一性
                    if (!existingItem.getDictItemKey().equals(dictItemDTO.getDictItemKey()) ||
                            !existingItem.getDictTypeId().equals(dictItemDTO.getDictTypeId())) {
                        return dictItemRepository.countByDictTypeIdAndDictItemKeyAndNotDeleted(
                                dictItemDTO.getDictTypeId(), dictItemDTO.getDictItemKey())
                                .flatMap(count -> {
                                    if (count > 0) {
                                        return Mono.error(new IllegalArgumentException("字典项键已存在"));
                                    }
                                    return updateDictItemEntity(existingItem, dictItemDTO);
                                });
                    }
                    return updateDictItemEntity(existingItem, dictItemDTO);
                })
                .flatMap(this::enrichDictItemWithType)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("字典项不存在")));
    }

    private Mono<SysDictItem> updateDictItemEntity(SysDictItem existingItem, SysDictItemUpdateDTO dictItemDTO) {
        sysDictItemMapper.updateEntity(dictItemDTO, existingItem);
        existingItem.setUpdateTime(LocalDateTime.now());
        return dictItemRepository.save(existingItem);
    }

    @Override
    @Transactional
    public Mono<Void> deleteDictItem(Long id) {
        log.debug("删除字典项, ID: {}", id);
        return dictItemRepository.findByIdAndNotDeleted(id)
                .flatMap(dictItem -> {
                    // 逻辑删除
                    dictItem.setIsDeleted(SystemConstants.IS_DELETED);
                    dictItem.setUpdateTime(LocalDateTime.now());
                    return dictItemRepository.save(dictItem);
                })
                .then();
    }
    
    @Override
    @Transactional
    public Mono<Void> updateDictItemStatus(Long id, Boolean status) {
        log.debug("更新字典项状态, ID: {}, 状态: {}", id, status);
        // 将Boolean状态转换为Integer
        Integer statusValue = status ? SystemConstants.STATUS_NORMAL : SystemConstants.STATUS_DISABLED;
        return dictItemRepository.updateStatusById(id, statusValue).then();
    }
    
    /**
     * 辅助方法
     */
    
    /**
     * 使用字典类型信息丰富字典项VO
     * 
     * @param dictItem 字典项实体
     * @param dictType 字典类型实体
     * @return 丰富后的字典项VO
     */
    private Mono<SysDictItemVO> enrichWithDictType(SysDictItem dictItem, SysDictType dictType) {
        SysDictItemVO vo = sysDictItemMapper.toVO(dictItem);
        sysDictItemMapper.setDictTypeInfo(vo, dictType);
        return Mono.just(vo);
    }
    
    /**
     * 丰富字典项并转换为VO
     * 
     * @param dictItem 字典项实体
     * @return 丰富后的字典项VO
     */
    private Mono<SysDictItemVO> enrichDictItemWithType(SysDictItem dictItem) {
        return dictTypeService.getDictTypeEntityById(dictItem.getDictTypeId())
                .flatMap(dictType -> enrichWithDictType(dictItem, dictType))
                .switchIfEmpty(Mono.just(sysDictItemMapper.toVO(dictItem)));
    }
    
    /**
     * 创建分页结果对象
     */
    private <T> PageResult<T> createPageResult(List<T> items, Long total, SysDictItemDTO dictItemDTO) {
        PageResult<T> pageResult = new PageResult<>();
        pageResult.setRecords(items);
        pageResult.setTotal(total);
        pageResult.setSize(dictItemDTO.getPageSize());
        pageResult.setCurrent(dictItemDTO.getCurrentPage());
        
        // 计算总页数
        long size = dictItemDTO.getPageSize();
        long pages = (total + size - 1) / size;
        pageResult.setPages(pages);
        
        return pageResult;
    }
} 