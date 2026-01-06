package com.ryu.blog.service;

import com.ryu.blog.dto.SysDictItemDTO;
import com.ryu.blog.dto.SysDictItemSaveDTO;
import com.ryu.blog.dto.SysDictItemUpdateDTO;
import com.ryu.blog.entity.SysDictItem;
import com.ryu.blog.vo.PageResult;
import com.ryu.blog.vo.SysDictItemVO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 系统字典项服务接口
 *
 * @author ryu 475118582@qq.com
 */
public interface SysDictItemService {

    /**
     * 分页查询字典项
     *
     * @param dictItemDTO 查询条件DTO
     * @return 字典项分页结果
     */
    Mono<PageResult<SysDictItemVO>> getDictItemPage(SysDictItemDTO dictItemDTO);

    /**
     * 根据ID获取字典项
     *
     * @param id 字典项ID
     * @return 字典项视图对象
     */
    Mono<SysDictItemVO> getDictItemById(Long id);
    
    /**
     * 根据ID获取字典项实体
     *
     * @param id 字典项ID
     * @return 字典项实体
     */
    Mono<SysDictItem> getDictItemEntityById(Long id);
    
    /**
     * 根据字典类型编码获取字典项列表
     *
     * @param dictType 字典类型编码
     * @return 字典项列表
     */
    Flux<SysDictItemVO> getDictItemsByType(String dictType);
    
    /**
     * 根据字典类型ID获取字典项列表
     *
     * @param dictTypeId 字典类型ID
     * @return 字典项列表
     */
    Flux<SysDictItemVO> getDictItemsByTypeId(Long dictTypeId);
    
    /**
     * 批量获取多个字典类型的字典项（按编码）
     *
     * @param dictTypes 字典类型编码列表
     * @return 字典类型编码 -> 字典项列表的映射
     */
    Mono<Map<String, List<SysDictItemVO>>> batchGetDictItems(List<String> dictTypes);
    
    /**
     * 批量获取多个字典类型的字典项（按ID）
     *
     * @param dictTypeIds 字典类型ID列表
     * @return 字典类型ID -> 字典项列表的映射
     */
    Mono<Map<Long, List<SysDictItemVO>>> batchGetDictItemsByIds(List<Long> dictTypeIds);
    
    /**
     * 根据字典类型编码和键获取字典项值
     *
     * @param dictType 字典类型编码
     * @param key 字典项键
     * @return 字典项值
     */
    Mono<String> getDictItemValue(String dictType, String key);
    
    /**
     * 根据字典类型编码和键获取字典项值，支持默认值
     *
     * @param dictType 字典类型编码
     * @param key 字典项键
     * @param defaultValue 默认值
     * @return 字典项值
     */
    Mono<String> getDictItemValue(String dictType, String key, String defaultValue);
    
    /**
     * 根据字典类型ID和键获取字典项值
     *
     * @param dictTypeId 字典类型ID
     * @param key 字典项键
     * @return 字典项值
     */
    Mono<String> getDictItemValueById(Long dictTypeId, String key);
    
    /**
     * 根据字典类型ID和键获取字典项值，支持默认值
     *
     * @param dictTypeId 字典类型ID
     * @param key 字典项键
     * @param defaultValue 默认值
     * @return 字典项值
     */
    Mono<String> getDictItemValueById(Long dictTypeId, String key, String defaultValue);

    /**
     * 创建字典项
     *
     * @param saveDTO 字典项保存DTO
     * @return 创建的字典项视图对象
     */
    Mono<SysDictItemVO> createDictItem(SysDictItemSaveDTO saveDTO);

    /**
     * 更新字典项
     *
     * @param dictItemDTO 字典项DTO
     * @return 更新的字典项视图对象
     */
    Mono<SysDictItemVO> updateDictItem(SysDictItemUpdateDTO dictItemDTO);

    /**
     * 删除字典项
     *
     * @param id 字典项ID
     * @return 操作结果
     */
    Mono<Void> deleteDictItem(Long id);
    
    /**
     * 更新字典项状态
     *
     * @param id 字典项ID
     * @param status 状态
     * @return 操作结果
     */
    Mono<Void> updateDictItemStatus(Long id, Boolean status);
    
    /**
     * 缓存管理
     */
    
    /**
     * 清除所有字典项缓存
     *
     * @return 操作结果
     */
    Mono<Boolean> clearAllCache();
    
    /**
     * 刷新指定字典类型的字典项缓存（按编码）
     *
     * @param dictType 字典类型编码
     * @return 操作结果
     */
    Mono<Boolean> refreshCache(String dictType);
    
    /**
     * 刷新指定字典类型的字典项缓存（按ID）
     *
     * @param dictTypeId 字典类型ID
     * @return 操作结果
     */
    Mono<Boolean> refreshCacheById(Long dictTypeId);
} 