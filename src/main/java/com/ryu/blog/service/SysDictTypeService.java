package com.ryu.blog.service;

import com.ryu.blog.dto.SysDictTypeAddDTO;
import com.ryu.blog.dto.SysDictTypeUpdateDTO;
import com.ryu.blog.dto.sysDictTypeQueryDTO;
import com.ryu.blog.entity.SysDictType;
import com.ryu.blog.vo.PageResult;
import com.ryu.blog.vo.SysDictTypeVO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 系统字典类型服务接口
 *
 * @author ryu 475118582@qq.com
 */
public interface SysDictTypeService {

    /**
     * 基础查询操作
     */
    
    /**
     * 分页查询字典类型
     *
     * @param queryDTO 查询条件DTO
     * @return 字典类型分页结果
     */
    Mono<PageResult<SysDictTypeVO>> getDictTypePage(sysDictTypeQueryDTO queryDTO);
    
    /**
     * 获取所有字典类型
     *
     * @return 字典类型视图对象列表
     */
    Flux<SysDictTypeVO> getAllDictTypes();

    /**
     * 根据ID获取字典类型
     *
     * @param id 字典类型ID
     * @return 字典类型视图对象
     */
    Mono<SysDictTypeVO> getDictTypeById(Long id);
    
    /**
     * 根据ID获取字典类型实体
     *
     * @param id 字典类型ID
     * @return 字典类型实体
     */
    Mono<SysDictType> getDictTypeEntityById(Long id);

    /**
     * 数据操作
     */
    
    /**
     * 创建字典类型
     *
     * @param dictTypeDTO 字典类型DTO
     * @return 创建的字典类型视图对象
     */
    Mono<SysDictTypeVO> createDictType(SysDictTypeAddDTO dictTypeDTO);

    /**
     * 更新字典类型
     *
     * @param dictTypeDTO 字典类型DTO
     * @return 更新的字典类型视图对象
     */
    Mono<SysDictTypeVO> updateDictType(SysDictTypeUpdateDTO dictTypeDTO);

    /**
     * 删除字典类型
     *
     * @param id 字典类型ID
     * @return 操作结果
     */
    Mono<Void> deleteDictType(Long id);
    
    /**
     * 批量删除字典类型
     *
     * @param ids 字典类型ID列表
     * @return 操作结果
     */
    Mono<Void> batchDeleteDictTypes(List<String> ids);
} 