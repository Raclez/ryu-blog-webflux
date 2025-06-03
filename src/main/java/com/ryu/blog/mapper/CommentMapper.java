package com.ryu.blog.mapper;

import com.ryu.blog.dto.CommentQueryDTO;
import com.ryu.blog.entity.Comment;
import com.ryu.blog.entity.User;
import com.ryu.blog.vo.CommentTreeVO;
import com.ryu.blog.vo.CommentVO;
import org.mapstruct.*;

import java.util.List;

/**
 * 评论实体映射器
 * 
 * @author ryu 475118582@qq.com
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CommentMapper {
    
    /**
     * 将Comment实体转换为CommentVO
     * 
     * @param comment 评论实体
     * @return CommentVO
     */
    CommentVO toCommentVO(Comment comment);
    
    /**
     * 将Comment实体转换为CommentTreeVO
     * 
     * @param comment 评论实体
     * @return CommentTreeVO
     */
    CommentTreeVO toCommentTreeVO(Comment comment);
    
    /**
     * 将Comment实体和用户信息转换为CommentTreeVO
     * 
     * @param comment 评论实体
     * @param user 用户实体
     * @return CommentTreeVO
     */
    @Mapping(target = "userName", source = "user.username")
    @Mapping(target = "userAvatar", source = "user.avatar")
    @Mapping(target = "id", source = "comment.id")
    @Mapping(target = "content", source = "comment.content")
    @Mapping(target = "postId", source = "comment.postId")
    @Mapping(target = "userId", source = "comment.userId")
    @Mapping(target = "parentCommentId", source = "comment.parentCommentId")
    @Mapping(target = "likeCount", source = "comment.likeCount")
    @Mapping(target = "isEdited", source = "comment.isEdited")
    @Mapping(target = "editTime", source = "comment.editTime")
    @Mapping(target = "createTime", source = "comment.createTime")
    @Mapping(target = "updateTime", source = "comment.updateTime")
    @Mapping(target = "status", source = "comment.status")
    CommentTreeVO toCommentTreeVO(Comment comment, User user);
    
    /**
     * 将Comment实体、用户信息和子评论列表转换为CommentTreeVO
     * 
     * @param comment 评论实体
     * @param user 用户实体
     * @param children 子评论列表
     * @return CommentTreeVO
     */
    @Mapping(target = "userName", source = "user.username")
    @Mapping(target = "userAvatar", source = "user.avatar")
    @Mapping(target = "children", source = "children")
    @Mapping(target = "id", source = "comment.id")
    @Mapping(target = "content", source = "comment.content")
    @Mapping(target = "postId", source = "comment.postId")
    @Mapping(target = "userId", source = "comment.userId")
    @Mapping(target = "parentCommentId", source = "comment.parentCommentId")
    @Mapping(target = "likeCount", source = "comment.likeCount")
    @Mapping(target = "isEdited", source = "comment.isEdited")
    @Mapping(target = "editTime", source = "comment.editTime")
    @Mapping(target = "createTime", source = "comment.createTime")
    @Mapping(target = "updateTime", source = "comment.updateTime")
    @Mapping(target = "status", source = "comment.status")
    CommentTreeVO toCommentTreeVO(Comment comment, User user, List<CommentTreeVO> children);
    
    /**
     * 将CommentQueryDTO转换为Comment实体
     * 
     * @param dto 评论查询DTO
     * @return Comment
     */
    Comment toComment(CommentQueryDTO dto);
}