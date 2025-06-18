package com.ryu.blog.service;

import com.ryu.blog.dto.TagCreateDTO;
import com.ryu.blog.dto.TagListDTO;
import com.ryu.blog.dto.TagUpdateDTO;
import com.ryu.blog.entity.Tag;
import com.ryu.blog.vo.PageResult;
import com.ryu.blog.vo.TagVO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 标签服务接口
 * @author ryu
 */
public interface TagService {

    /**
     * 创建标签
     * @param tagCreateDTO 标签创建DTO
     * @return 是否创建成功
     */
    Mono<Boolean> createTag(TagCreateDTO tagCreateDTO);

    /**
     * 更新标签
     * @param tagUpdateDTO 标签更新DTO
     * @return 是否更新成功
     */
    Mono<Boolean> updateTag(TagUpdateDTO tagUpdateDTO);

    /**
     * 根据ID获取标签
     * @param id 标签ID
     * @return 标签VO信息
     */
    Mono<TagVO> getTagById(Long id);

    /**
     * 删除标签
     * @param id 标签ID
     * @return 是否删除成功
     */
    Mono<Boolean> deleteTag(Long id);

    /**
     * 获取所有标签
     * @param withCount 是否包含文章数量
     * @return 标签VO列表
     */
    Flux<TagVO> getAllTags(boolean withCount);

    /**
     * 根据文章ID获取标签列表
     * @param articleId 文章ID
     * @return 标签VO列表
     */
    Flux<TagVO> getTagsByArticleId(Long articleId);

    /**
     * 为文章添加标签
     * @param articleId 文章ID
     * @param tagIds 标签ID列表
     * @return 是否添加成功
     */
    Mono<Boolean> addTagsToArticle(Long articleId, List<Long> tagIds);

    /**
     * 移除文章的标签
     * @param articleId 文章ID
     * @return 是否移除成功
     */
    Mono<Boolean> removeTagsFromArticle(Long articleId);

    /**
     * 检查标签名称是否存在
     * @param name 标签名称
     * @return 是否存在
     */
    Mono<Boolean> checkTagNameExists(String name);

    /**
     * 获取热门标签
     * @param limit 限制数量
     * @return 标签VO列表
     */
    Flux<TagVO> getHotTags(int limit);
    
    /**
     * 分页查询标签
     * @param tagListDTO 标签查询条件
     * @return 标签分页结果
     */
    Mono<PageResult<TagVO>> getTagByPage(TagListDTO tagListDTO);
} 