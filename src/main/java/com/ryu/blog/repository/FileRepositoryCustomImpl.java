package com.ryu.blog.repository;

import com.ryu.blog.dto.FileSearchDTO;
import com.ryu.blog.entity.File;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;

/**
 * 文件仓储自定义片段实现
 *
 * @author ryu 475118582@qq.com
 */
@Slf4j
@RequiredArgsConstructor
public class FileRepositoryCustomImpl implements FileRepositoryCustom {

    /**
     * 允许作为排序字段的列名白名单 —— 排序字段来自客户端，必须限制取值范围，
     * 否则等于把任意字符串拼进 ORDER BY。
     */
    private static final Set<String> SORTABLE_COLUMNS = Set.of(
            "create_time", "update_time", "upload_time", "file_size", "file_name", "file_type");

    private final R2dbcEntityTemplate template;

    @Override
    public Flux<File> searchFiles(FileSearchDTO searchDTO, int limit, long offset) {
        Sort.Direction direction = "asc".equalsIgnoreCase(searchDTO.getOrderDirection())
                ? Sort.Direction.ASC : Sort.Direction.DESC;

        Query query = Query.query(searchCriteria(searchDTO))
                // Sort.by 的签名是 by(Direction, String...)，方向在前列名在后
                .sort(Sort.by(direction, resolveSortColumn(searchDTO.getOrderBy()))
                        // 次级键：排序字段（时间/大小/文件名）都可能重复，
                        // 只按它排时翻页顺序是不确定的 —— 同一份数据连续翻两页
                        // 可能重复出现或漏掉记录。补一个唯一的 id 兜底。
                        .and(Sort.by(direction, "id")))
                .limit(limit)
                .offset(offset);

        return template.select(File.class)
                .from("t_file")
                .matching(query)
                .all();
    }

    @Override
    public Mono<Long> countFiles(FileSearchDTO searchDTO) {
        return template.select(File.class)
                .from("t_file")
                .matching(Query.query(searchCriteria(searchDTO)))
                .count();
    }

    @Override
    public Mono<File> findByFilePathAndIsDeleted(String path, Integer isDeleted) {
        return template.select(File.class)
                .from("t_file")
                .matching(Query.query(Criteria.where("file_path").is(path)
                        .and("is_deleted").is(isDeleted)))
                .one();
    }

    @Override
    public Mono<File> findByFilePathAndChecksum(String path, String checksum, Integer isDeleted) {
        return template.select(File.class)
                .from("t_file")
                .matching(Query.query(Criteria.where("file_path").is(path)
                        .and("checksum").is(checksum)
                        .and("is_deleted").is(isDeleted)))
                .one();
    }

    /**
     * 构建搜索条件
     *
     * @param searchDTO 搜索条件
     * @return Criteria
     */
    private Criteria searchCriteria(FileSearchDTO searchDTO) {
        Criteria criteria = Criteria.where("is_deleted").is(0);

        if (searchDTO.getKeyword() != null && !searchDTO.getKeyword().isEmpty()) {
            criteria = criteria.and(Criteria.where("file_name").like("%" + searchDTO.getKeyword() + "%")
                    .or("description").like("%" + searchDTO.getKeyword() + "%"));
        }

        if (searchDTO.getFileTypes() != null && !searchDTO.getFileTypes().isEmpty()) {
            criteria = criteria.and(Criteria.where("file_type").in(searchDTO.getFileTypes()));
        }

        if (searchDTO.getCreatorId() != null) {
            criteria = criteria.and(Criteria.where("creator_id").is(searchDTO.getCreatorId()));
        }

        if (searchDTO.getUploadStartTime() != null) {
            criteria = criteria.and(Criteria.where("upload_time")
                    .greaterThanOrEquals(toLocalDateTime(searchDTO.getUploadStartTime())));
        }

        if (searchDTO.getUploadEndTime() != null) {
            criteria = criteria.and(Criteria.where("upload_time")
                    .lessThanOrEquals(toLocalDateTime(searchDTO.getUploadEndTime())));
        }

        if (searchDTO.getMinSize() != null) {
            criteria = criteria.and(Criteria.where("file_size").greaterThanOrEquals(searchDTO.getMinSize()));
        }

        if (searchDTO.getMaxSize() != null) {
            criteria = criteria.and(Criteria.where("file_size").lessThanOrEquals(searchDTO.getMaxSize()));
        }

        return criteria;
    }

    /**
     * 把客户端传来的排序字段映射到真实列名，不在白名单内的一律按上传时间排序。
     *
     * @param orderBy 客户端排序字段（驼峰或下划线均可）
     * @return 数据库列名
     */
    private String resolveSortColumn(String orderBy) {
        if (orderBy == null || orderBy.isBlank()) {
            return "upload_time";
        }
        // DTO 里写的是驼峰（uploadTime），列名是下划线（upload_time）
        String snake = orderBy.trim().toLowerCase().replaceAll("([a-z0-9])([A-Z])", "$1_$2");
        return SORTABLE_COLUMNS.contains(snake) ? snake : "upload_time";
    }

    private LocalDateTime toLocalDateTime(java.util.Date date) {
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }
}
