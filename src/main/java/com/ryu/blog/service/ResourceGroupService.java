package com.ryu.blog.service;

import com.ryu.blog.dto.ResourceGroupCreateDTO;
import com.ryu.blog.dto.ResourceGroupFileDTO;
import com.ryu.blog.dto.ResourceGroupQueryDTO;
import com.ryu.blog.dto.ResourceGroupUpdateDTO;
import com.ryu.blog.vo.PageResult;
import com.ryu.blog.vo.ResourceGroupVO;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 资源组服务接口
 *
 * @author ryu
 */
public interface ResourceGroupService {

    /**
     * 创建资源组
     *
     * @param dto 创建DTO
     * @return 创建结果
     * @deprecated 请使用带ServerWebExchange参数的方法代替
     */
    @Deprecated
    default Mono<Void> createResourceGroup(ResourceGroupCreateDTO dto) {
        throw new UnsupportedOperationException("请使用带ServerWebExchange参数的方法");
    }
    
    /**
     * 创建资源组（响应式）
     *
     * @param dto 创建DTO
     * @param exchange ServerWebExchange实例
     * @return 创建结果
     */
    Mono<Void> createResourceGroup(ResourceGroupCreateDTO dto, ServerWebExchange exchange);

    /**
     * 更新资源组
     *
     * @param dto 更新DTO
     * @return 更新结果
     */
    Mono<Void> updateResourceGroup(ResourceGroupUpdateDTO dto);

    /**
     * 删除资源组
     *
     * @param id 资源组ID
     * @return 删除结果
     */
    Mono<Void> deleteResourceGroup(Long id);

    /**
     * 获取用户所有资源组
     *
     * @param userId 用户ID
     * @return 资源组列表
     */
    Flux<ResourceGroupVO> getUserResourceGroups(Long userId);

    /**
     * 添加文件到资源组
     *
     * @param dto 资源组文件DTO
     * @return 操作结果
     */
    Mono<Void> addFilesToGroup(ResourceGroupFileDTO dto);

    /**
     * 从资源组移除文件
     *
     * @param dto 资源组文件DTO
     * @return 操作结果
     */
    Mono<Void> removeFilesFromGroup(ResourceGroupFileDTO dto);

    /**
     * 获取资源组中的文件ID列表
     */
    Mono<PageResult<Long>> getGroupFileIds(ResourceGroupQueryDTO queryDTO);

    /**
     * 检查资源组名是否存在
     *
     * @param groupName 资源组名
     * @param excludeId 排除的资源组ID（更新时使用）
     * @return 是否存在
     */
    Mono<Boolean> checkGroupNameExists(String groupName, Long excludeId);

    /**
     * 获取文件所属的资源组
     *
     * @param fileId 文件ID
     * @return 资源组列表
     */
    Flux<ResourceGroupVO> getFileResourceGroups(Long fileId);
} 