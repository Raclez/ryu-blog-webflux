package com.ryu.blog.mapper;

import com.ryu.blog.dto.PostVersionDTO;
import com.ryu.blog.entity.PostVersion;
import com.ryu.blog.entity.User;
import com.ryu.blog.vo.ContentDiffVO;
import com.ryu.blog.vo.PostVersionDetailVO;
import com.ryu.blog.vo.PostVersionVO;
import org.mapstruct.*;

import java.util.List;

/**
 * 文章版本实体映射器
 * 
 * @author ryu 475118582@qq.com
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PostVersionMapper {
    
    /**
     * 将PostVersion实体转换为PostVersionVO
     * 
     * @param postVersion 文章版本实体
     * @return PostVersionVO
     */
    @Mapping(target = "latest", source = "isLatest")
    PostVersionVO toPostVersionVO(PostVersion postVersion);
    
    /**
     * 将PostVersion实体和编辑者信息转换为PostVersionVO
     * 
     * @param postVersion 文章版本实体
     * @param editorUser 编辑者用户实体
     * @return PostVersionVO
     */
    @Mapping(target = "id", source = "postVersion.id")
    @Mapping(target = "postId", source = "postVersion.postId")
    @Mapping(target = "version", source = "postVersion.version")
    @Mapping(target = "createTime", source = "postVersion.createTime")
    @Mapping(target = "editor", source = "editorUser.username")
    @Mapping(target = "changeLog", source = "postVersion.changeLog")
    @Mapping(target = "wordCount", source = "postVersion.wordCount")
    @Mapping(target = "tags", source = "postVersion.tags")
    @Mapping(target = "latest", source = "postVersion.isLatest")
    @Mapping(target = "description", source = "postVersion.description")
    @Mapping(target = "duration", source = "postVersion.duration")
    @Mapping(target = "modifyCount", source = "postVersion.modifyCount")
    PostVersionVO toPostVersionVO(PostVersion postVersion, User editorUser);
    
    /**
     * 将PostVersion实体转换为PostVersionDetailVO
     * 
     * @param postVersion 文章版本实体
     * @return PostVersionDetailVO
     */
    @Mapping(target = "latest", source = "isLatest")
    PostVersionDetailVO toPostVersionDetailVO(PostVersion postVersion);
    
    /**
     * 将PostVersion实体、编辑者信息和差异列表转换为PostVersionDetailVO
     * 
     * @param postVersion 文章版本实体
     * @param editorUser 编辑者用户实体
     * @param diffs 差异列表
     * @return PostVersionDetailVO
     */
    @Mapping(target = "id", source = "postVersion.id")
    @Mapping(target = "postId", source = "postVersion.postId")
    @Mapping(target = "version", source = "postVersion.version")
    @Mapping(target = "createTime", source = "postVersion.createTime")
    @Mapping(target = "editor", source = "editorUser.username")
    @Mapping(target = "changeLog", source = "postVersion.changeLog")
    @Mapping(target = "wordCount", source = "postVersion.wordCount")
    @Mapping(target = "tags", source = "postVersion.tags")
    @Mapping(target = "latest", source = "postVersion.isLatest")
    @Mapping(target = "description", source = "postVersion.description")
    @Mapping(target = "duration", source = "postVersion.duration")
    @Mapping(target = "modifyCount", source = "postVersion.modifyCount")
    @Mapping(target = "content", source = "postVersion.content")
    @Mapping(target = "diffs", source = "diffs")
    PostVersionDetailVO toPostVersionDetailVO(PostVersion postVersion, User editorUser, List<ContentDiffVO> diffs);
    
    /**
     * 将PostVersionDTO转换为PostVersion实体
     * 
     * @param dto 文章版本DTO
     * @return PostVersion
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "content", ignore = true)
    @Mapping(target = "editor", ignore = true)
    @Mapping(target = "changeLog", ignore = true)
    @Mapping(target = "wordCount", ignore = true)
    @Mapping(target = "tags", ignore = true)
    @Mapping(target = "isLatest", ignore = true)
    @Mapping(target = "description", ignore = true)
    @Mapping(target = "duration", ignore = true)
    @Mapping(target = "modifyCount", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    PostVersion toPostVersion(PostVersionDTO dto);
} 