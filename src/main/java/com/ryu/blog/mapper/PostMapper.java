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

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "views", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "publishTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "seoMeta", ignore = true)
    Posts toEntity(PostCreateDTO dto);

    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "views", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "publishTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "seoMeta", ignore = true)
    void updateEntityFromDTO(PostUpdateDTO dto, @MappingTarget Posts entity);

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

    @Mapping(target = "tagsIds", ignore = true)
    @Mapping(target = "coverImageUrl", ignore = true)
    @Mapping(target = "seoTitle", ignore = true)
    @Mapping(target = "seoDescription", ignore = true)
    PostDetailVO toDetailVO(Posts entity);

    @Mapping(target = "categoryId", ignore = true)
    @Mapping(target = "categoryName", ignore = true)
    @Mapping(target = "tags", ignore = true)
    @Mapping(target = "authorName", ignore = true)
    @Mapping(target = "authorAvatar", ignore = true)
    @Mapping(target = "coverImageUrl", ignore = true)
    @Mapping(target = "commentCount", ignore = true)
    @Mapping(target = "likeCount", ignore = true)
    PostFrontListVO toFrontListVO(Posts entity);

    List<PostFrontListVO> toFrontListVOList(List<Posts> entities);

    @Mapping(target = "categoryId", ignore = true)
    @Mapping(target = "categoryName", ignore = true)
    @Mapping(target = "authorName", ignore = true)
    PostAdminListVO toAdminListVO(Posts entity);

    List<PostAdminListVO> toAdminListVOList(List<Posts> entities);

    default PostDetailVO toDetailVO(Posts entity, List<Long> tagsIds, String coverImageUrl, String seoTitle, String seoDescription) {
        PostDetailVO vo = toDetailVO(entity);
        if (vo != null) {
            vo.setTagsIds(tagsIds);
            vo.setCoverImageUrl(coverImageUrl);
            vo.setSeoTitle(seoTitle);
            vo.setSeoDescription(seoDescription);
        }
        return vo;
    }

    default PostFrontListVO toFrontListVO(Posts entity, Long categoryId, String categoryName, List<String> tags,
            String authorName, String authorAvatar, String coverImageUrl, Integer commentCount, Integer likeCount) {
        PostFrontListVO vo = toFrontListVO(entity);
        if (vo != null) {
            vo.setCategoryId(categoryId);
            vo.setCategoryName(categoryName);
            vo.setTags(tags);
            vo.setAuthorName(authorName);
            vo.setAuthorAvatar(authorAvatar);
            vo.setCoverImageUrl(coverImageUrl);
            vo.setCommentCount(commentCount);
            vo.setLikeCount(likeCount);
        }
        return vo;
    }

    default PostAdminListVO toAdminListVO(Posts entity, Long categoryId, String categoryName, String authorName) {
        PostAdminListVO vo = toAdminListVO(entity);
        if (vo != null) {
            vo.setCategoryId(categoryId);
            vo.setCategoryName(categoryName);
            vo.setAuthorName(authorName);
        }
        return vo;
    }
}