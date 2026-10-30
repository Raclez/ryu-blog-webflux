package com.ryu.blog.repository;

import com.ryu.blog.dto.FileSearchDTO;
import com.ryu.blog.entity.File;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 文件仓储的自定义片段
 *
 * <p>Spring Data 的派生查询无法表达「动态条件 + 动态排序 + 分页」，
 * 这类查询统一收敛到这里，保证 Repository 是唯一的数据访问入口。
 *
 * @author ryu 475118582@qq.com
 */
public interface FileRepositoryCustom {

    /**
     * 按搜索条件分页查询文件
     *
     * @param searchDTO 搜索条件
     * @param limit     每页大小
     * @param offset    偏移量
     * @return 文件列表
     */
    Flux<File> searchFiles(FileSearchDTO searchDTO, int limit, long offset);

    /**
     * 统计符合搜索条件的文件数量
     *
     * @param searchDTO 搜索条件
     * @return 文件数量
     */
    Mono<Long> countFiles(FileSearchDTO searchDTO);

    /**
     * 按存储路径查询文件
     *
     * @param path     存储路径
     * @param isDeleted 删除标记
     * @return 文件
     */
    Mono<File> findByFilePathAndIsDeleted(String path, Integer isDeleted);

    /**
     * 按存储路径与校验和查询文件
     *
     * @param path      存储路径
     * @param checksum  校验和
     * @param isDeleted 删除标记
     * @return 文件
     */
    Mono<File> findByFilePathAndChecksum(String path, String checksum, Integer isDeleted);
}
