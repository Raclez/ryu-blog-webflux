package com.ryu.blog.strategy;

import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 可选能力：批量删除。
 *
 * <p>单条 {@code deleteFile} 属于核心接口；批量是它的「优化实现」，
 * 不支持批量删除的策略照样能用核心接口逐条删。
 *
 * @author ryu
 */
public interface BatchDeleteCapable {

    /**
     * 批量删除文件
     *
     * @param paths 文件路径列表
     * @return 删除结果，键为文件路径，值为是否成功
     */
    Mono<Map<String, Boolean>> batchDeleteFiles(List<String> paths);
}
