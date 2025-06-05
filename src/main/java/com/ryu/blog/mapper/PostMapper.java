package com.ryu.blog.mapper;

import com.ryu.blog.dto.PostCreateDTO;
import com.ryu.blog.dto.PostStatusDTO;
import com.ryu.blog.dto.PostUpdateDTO;
import com.ryu.blog.entity.Posts;
import com.ryu.blog.vo.PostAdminListVO;
import com.ryu.blog.vo.PostDetailVO;
import com.ryu.blog.vo.PostFrontListVO;
import org.mapstruct.*;

import java.util.List;

/**
 * 文章对象映射接口
 * 使用MapStruct进行DTO、VO和实体之间的自动转换
 *
 * @author ryu
 */
@Mapper(componentModel = "spring", 
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PostMapper {

    /**
     * 将创建DTO转换为实体
     * 忽略一些由系统自动设置的字段
     *
     * @param dto 创建DTO
     * @return 实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "views", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "publishTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "seoMeta", ignore = true) // SEO信息需要特殊处理
    Posts toEntity(PostCreateDTO dto);

    /**
     * 将更新DTO应用到实体
     * 忽略一些由系统自动设置的字段
     *
     * @param dto 更新DTO
     * @param entity 目标实体
     */
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "views", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "publishTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "seoMeta", ignore = true) // SEO信息需要特殊处理
    void updateEntityFromDTO(PostUpdateDTO dto, @MappingTarget Posts entity);

    /**
     * 将状态DTO应用到实体
     * 只更新状态字段，其他字段保持不变
     *
     * @param dto 状态DTO
     * @param entity 目标实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "title", ignore = true)
    @Mapping(target = "content", ignore = true)
    @Mapping(target = "excerpt", ignore = true)
    @Mapping(target = "seoMeta", ignore = true)
    @Mapping(target = "coverImageId", ignore = true)
    @Mapping(target = "isOriginal", ignore = true)
    @Mapping(target = "sourceUrl", ignore = true)
    @Mapping(target = "sort", ignore = true)
    @Mapping(target = "allowComment", ignore = true)
    @Mapping(target = "visibility", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "license", ignore = true)
    @Mapping(target = "scheduleTime", ignore = true)
    @Mapping(target = "views", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "publishTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    void updateStatusFromDTO(PostStatusDTO dto, @MappingTarget Posts entity);

    /**
     * 将实体转换为详情VO
     * 包含文章的详细信息
     *
     * @param entity 实体
     * @return 详情VO
     */
    @Mapping(target = "tagsIds", ignore = true) // 标签需要单独处理
    @Mapping(target = "coverImageUrl", ignore = true) // 封面图片URL需要单独设置
    @Mapping(target = "seoTitle", ignore = true) // SEO标题需要从seoMeta解析
    @Mapping(target = "seoDescription", ignore = true) // SEO描述需要从seoMeta解析
    PostDetailVO toDetailVO(Posts entity);

    /**
     * 将实体转换为前台列表VO
     * 包含前台展示所需的文章信息
     *
     * @param entity 实体
     * @return 前台列表VO
     */
    @Mapping(target = "categoryId", ignore = true) // 分类ID需要单独设置
    @Mapping(target = "categoryName", ignore = true) // 分类名称需要单独设置
    @Mapping(target = "tags", ignore = true) // 标签需要单独处理
    @Mapping(target = "authorName", ignore = true) // 作者名称需要单独设置
    @Mapping(target = "authorAvatar", ignore = true) // 作者头像需要单独设置
    @Mapping(target = "coverImageUrl", ignore = true) // 封面图片URL需要单独设置
    @Mapping(target = "commentCount", ignore = true) // 评论数需要单独计算
    @Mapping(target = "likeCount", ignore = true) // 点赞数需要单独计算
    PostFrontListVO toFrontListVO(Posts entity);

    /**
     * 将实体列表转换为前台列表VO列表
     *
     * @param entities 实体列表
     * @return 前台列表VO列表
     */
    List<PostFrontListVO> toFrontListVOList(List<Posts> entities);

    /**
     * 将实体转换为后台列表VO
     * 包含后台管理所需的文章信息
     *
     * @param entity 实体
     * @return 后台列表VO
     */
    @Mapping(target = "categoryId", ignore = true) // 分类ID需要单独设置
    @Mapping(target = "categoryName", ignore = true) // 分类名称需要单独设置
    @Mapping(target = "authorName", ignore = true) // 作者名称需要单独设置
    PostAdminListVO toAdminListVO(Posts entity);

    /**
     * 将实体列表转换为后台列表VO列表
     *
     * @param entities 实体列表
     * @return 后台列表VO列表
     */
    List<PostAdminListVO> toAdminListVOList(List<Posts> entities);
    
    /**
     * 在转换后设置后台VO的额外属性
     * 
     * @param vo 视图对象
     * @param categoryId 分类ID
     * @param categoryName 分类名称
     * @param authorName 作者名称
     * @return 更新后的视图对象
     */
    @AfterMapping
    default PostAdminListVO setAdminExtraProperties(
            PostAdminListVO vo, 
            Long categoryId,
            String categoryName, 
            String authorName) {
        vo.setCategoryId(categoryId);
        vo.setCategoryName(categoryName);
        vo.setAuthorName(authorName);
        return vo;
    }
    
    /**
     * 在转换后设置前台VO的额外属性
     * 
     * @param vo 前台视图对象
     * @param categoryId 分类ID
     * @param categoryName 分类名称
     * @param tags 标签列表
     * @param authorName 作者名称
     * @param authorAvatar 作者头像
     * @param coverImageUrl 封面图片URL
     * @param commentCount 评论数
     * @param likeCount 点赞数
     * @return 更新后的视图对象
     */
    @AfterMapping
    default PostFrontListVO setFrontExtraProperties(
            PostFrontListVO vo, 
            Long categoryId,
            String categoryName, 
            List<String> tags, 
            String authorName,
            String authorAvatar,
            String coverImageUrl,
            Integer commentCount,
            Integer likeCount) {
        vo.setCategoryId(categoryId);
        vo.setCategoryName(categoryName);
        vo.setTags(tags);
        vo.setAuthorName(authorName);
        vo.setAuthorAvatar(authorAvatar);
        vo.setCoverImageUrl(coverImageUrl);
        vo.setCommentCount(commentCount);
        vo.setLikeCount(likeCount);
        return vo;
    }
    
    /**
     * 在转换后设置详情VO的额外属性
     * 
     * @param vo 详情视图对象
     * @param tagsIds 标签ID列表
     * @param coverImageUrl 封面图片URL
     * @param seoTitle SEO标题
     * @param seoDescription SEO描述
     * @return 更新后的视图对象
     */
    @AfterMapping
    default PostDetailVO setDetailExtraProperties(
            PostDetailVO vo, 
            List<Long> tagsIds,
            String coverImageUrl,
            String seoTitle,
            String seoDescription) {
        vo.setTagsIds(tagsIds);
        vo.setCoverImageUrl(coverImageUrl);
        vo.setSeoTitle(seoTitle);
        vo.setSeoDescription(seoDescription);
        return vo;
    }
} 