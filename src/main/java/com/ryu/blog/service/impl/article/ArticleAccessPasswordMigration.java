package com.ryu.blog.service.impl.article;

import com.ryu.blog.dto.PostAccessPasswordProjection;
import com.ryu.blog.repository.PostsRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 文章访问密码的一次性数据迁移：把历史明文洗成 BCrypt 哈希
 *
 * <p><b>为什么需要它</b>：文章访问密码（{@code t_posts.password}）在 2026-10-30 之前
 * 是<b>明文</b>存着的。写路径已经改成只存哈希（见 {@code ArticleAccessPasswordPolicy}），
 * 但库里已有的历史行还留着明文 —— 它们会一直躺在库里，也一直躺在每次备份的文件里。
 *
 * <p><b>怎么做</b>：启动时把「密码不是 BCrypt 格式」的行捞出来，逐行重新编码写回。
 * <b>口令本身不变</b> —— 明文 {@code "abc"} 被换成 {@code BCrypt("abc")}，
 * 读者用 {@code "abc"} 照样能打开文章（校验走 {@code ArticleAccessPasswordPolicy.matches}，
 * 两种存法都认）。
 *
 * <p><b>幂等</b>：洗完之后没有行符合条件了，重启不会再动。
 * 已软删除的文章也一并洗 —— 明文留在软删除的行里同样是泄露面。
 *
 * <p><b>失败不影响启动</b>：迁移失败只记日志。理由是它是「锦上添花」的数据收口，
 * 不是启动的前提；把它做成启动的硬依赖，只会让一台连不上库的机器起不来。
 *
 * @author ryu
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleAccessPasswordMigration {

    private final PostsRepository postsRepository;

    @PostConstruct
    public void migrate() {
        postsRepository.findLegacyPlaintextAccessPasswords()
                .concatMap(this::rehash)
                // concatMap 保证逐行串行 —— 不并发提交多条 UPDATE，
                // 避免在事务外的连接池上把请求队列打满
                .reduce(0, Integer::sum)
                .doOnError(e -> log.error("文章访问密码明文→哈希 迁移失败: {}", e.getMessage(), e))
                .subscribe(
                        migrated -> log.info("文章访问密码明文→哈希 迁移完成，共改写 {} 行", migrated),
                        error -> log.error("文章访问密码明文→哈希 迁移失败（不影响启动）: {}", error.getMessage(), error)
                );
    }

    /**
     * 洗一行
     *
     * @param row 历史行
     * @return 实际改写的行数（0 表示没改）
     */
    private Mono<Integer> rehash(PostAccessPasswordProjection row) {
        String hashed = ArticleAccessPasswordPolicy.rehashIfPlaintext(row.getPassword());
        if (hashed == null || hashed.equals(row.getPassword())) {
            return Mono.just(0);
        }
        return postsRepository.updateAccessPassword(row.getId(), hashed);
    }
}
