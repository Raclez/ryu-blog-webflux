package com.ryu.blog.service;

import com.ryu.blog.dto.SysDictItemDTO;
import com.ryu.blog.dto.SysDictItemSaveDTO;
import com.ryu.blog.dto.SysDictItemUpdateDTO;
import com.ryu.blog.entity.SysDictItem;
import com.ryu.blog.vo.PageResult;
import com.ryu.blog.vo.SysDictItemVO;
import reactor.core.publisher.Mono;

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
} 