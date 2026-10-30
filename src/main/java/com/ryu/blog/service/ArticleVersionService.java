package com.ryu.blog.service;

import com.ryu.blog.dto.OperatorContext;
import com.ryu.blog.entity.PostVersion;
import com.ryu.blog.entity.Posts;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 文章版本服务接口
 */
public interface ArticleVersionService {

    /**
     * 创建文章版本
     *
     * @param article     文章
     * @param description 版本描述
     * @return 版本信息
     */
    Mono<PostVersion> createVersion(Posts article, String description);

    /**
     * 获取文章版本列表（游标分页）
     *
     * @param articleId 文章ID
     * @param cursor 游标（上一页最后一条记录的ID）
     * @param limit 每页数量
     * @return 版本列表
     */
    Flux<PostVersion> getVersions(Long articleId, Long cursor, Integer limit);

    /**
     * 获取文章指定版本
     *
     * @param articleId 文章ID
     * @param version   版本号
     * @return 版本信息
     */
    Mono<PostVersion> getVersion(Long articleId, Integer version);

    /**
     * 获取文章最新版本
     *
     * @param articleId 文章ID
     * @return 最新版本
     */
    Mono<PostVersion> getLatestVersion(Long articleId);

    /**
     * 回滚到指定版本
     *
     * @param articleId 文章ID
     * @param version   版本号
     * @param ctx       操作者上下文（用于归属校验：作者本人或「能管理他人」的管理者）
     * @return 回滚后的文章
     */
    Mono<Posts> rollbackToVersion(Long articleId, Integer version, OperatorContext ctx);

    /**
     * 删除文章版本
     *
     * @param id  版本ID
     * @param ctx 操作者上下文（用于归属校验：作者本人或「能管理他人」的管理者）
     * @return 是否删除成功
     */
    Mono<Boolean> deleteVersion(Long id, OperatorContext ctx);

    /**
     * 比较两个版本的差异
     *
     * @param articleId 文章ID
     * @param version1  版本1
     * @param version2  版本2
     * @return 差异信息
     */
    Mono<Map<String, Object>> compareVersions(Long articleId, Integer version1, Integer version2);
} 