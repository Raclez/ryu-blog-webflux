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
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PostMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "views", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "publishTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "seoMeta", ignore = true)
    // 状态由服务端按业务流程决定（草稿/待审核/发布），不接受创建请求指定
    @Mapping(target = "status", ignore = true)
    Posts toEntity(PostCreateDTO dto);

    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "views", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "publishTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "seoMeta", ignore = true)
    // schedule_time 这一列在项目里从来没有被读过：计划发布时间统一落在 publish_time 上
    // （见 PublishMode）。原先它是打开着的，一条编辑请求就会往这列写一个没人读的值。
    @Mapping(target = "scheduleTime", ignore = true)
    // 状态**只能**通过 PUT /posts/status 变更（能力校验 content:post:publish）。
    // 原先这条映射是打开的：一条普通的「编辑文章」请求就能把文章直接改成「已发布」，
    // 把发布闸门整个绕过去。DTO 里已经删掉了 status 字段，这里再 ignore 一次是双保险 ——
    // 将来有人把字段加回去，自动映射也不会生效。
    // ⚠️ 例外：publishMode 是显式字段，由 Service 层显式调用 applyPublishMode 处理，
    // 不走自动映射。
    @Mapping(target = "status", ignore = true)
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
    // 置顶与状态无关：改状态（发布/下架）不该顺手把文章置顶或取消置顶
    @Mapping(target = "isSticky", ignore = true)
    @Mapping(target = "visibility", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "license", ignore = true)
    @Mapping(target = "scheduleTime", ignore = true)
    @Mapping(target = "views", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "publishTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    // 改状态不应该动别名
    @Mapping(target = "slug", ignore = true)
    void updateStatusFromDTO(PostStatusDTO dto, @MappingTarget Posts entity);

    @Mapping(target = "tagsIds", ignore = true)
    @Mapping(target = "coverImageUrl", ignore = true)
    @Mapping(target = "seoTitle", ignore = true)
    @Mapping(target = "seoDescription", ignore = true)
    // categoryId 来自分类关联表、readingTime 由正文计算得出，
    // 都不在 t_posts 的列里，由 Service 层组装详情时补上
    @Mapping(target = "categoryId", ignore = true)
    @Mapping(target = "readingTime", ignore = true)
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