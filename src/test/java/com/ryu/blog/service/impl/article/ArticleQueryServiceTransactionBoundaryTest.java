package com.ryu.blog.service.impl.article;

import com.ryu.blog.dto.PostCategoryProjection;
import com.ryu.blog.entity.Posts;
import com.ryu.blog.entity.User;
import com.ryu.blog.mapper.PostMapper;
import com.ryu.blog.repository.PostsRepository;
import com.ryu.blog.repository.TagRepository;
import com.ryu.blog.repository.UserRepository;
import com.ryu.blog.service.ContentService;
import com.ryu.blog.service.FileService;
import com.ryu.blog.service.impl.ArticleServiceImpl;
import com.ryu.blog.vo.PageResult;
import com.ryu.blog.vo.PostAdminListVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code ArticleQueryService#getArticlePageVO} 的事务边界回归测试。
 *
 * <p><b>背景（2026-10-28 事故）</b>：该方法原先标注 {@code @Transactional(readOnly = true)}，
 * 事务会把整条调用链绑定到<b>同一条</b> R2DBC 连接上；而 {@code buildAdminListVOs}
 * 里用 {@code Mono.zip} 并发订阅「分类」和「用户」两条富化查询 —— 于是两个线程同时
 * 向同一条连接的 {@code RequestQueue} 提交请求。
 *
 * <p>asyncer 的 r2dbc-mysql 1.4.0 中，每个 {@code ReactorNettyClient} 持有一个
 * {@code RequestQueue}，其底层是 {@code Queues.small()} 得到的 {@code SpscArrayQueue}
 * <b>单生产者</b>队列；{@code submit()} 调 {@code offer()} 时并不持有内部锁
 * （那把 {@code ReentrantLock} 只在 {@code cancelAll} 里用）。两个生产者并发 offer
 * 时，后到者会读到尚未推进的 {@code producerIndex} 而误判为队满，抛
 * {@code IllegalStateException: Request queue is full}，该请求在发上线之前就被取消。
 *
 * <p>报文里只能看到 {@code 批量获取用户信息失败: Request queue is full} ——
 * 因为 {@code ArticleViewAssembler#batchGetUserInfo} 的 {@code onErrorResume}
 * 只打印了 {@code e.getMessage()} 并降级成空 Map，页面因此静默丢失作者信息。
 *
 * <p><b>为什么删事务是正确修法</b>：本方法全部是读操作，事务不提供任何实际收益
 * （count 与列表页之间的一致快照对分页列表本就无意义）；删掉之后每条查询各自从连接池
 * 取连接，{@code RequestQueue} 互不相干，竞态在物理上不存在 —— 这也正是前台路径
 * {@code ArticleViewAssembler#batchGetPostRelatedData}（5 路 zip、无事务）一直正常的原因。
 */
class ArticleQueryServiceTransactionBoundaryTest {

    private static Method pageMethod(Class<?> type) throws NoSuchMethodException {
        return type.getMethod("getArticlePageVO",
                int.class, int.class, String.class, Integer.class,
                Long.class, Long.class, String.class, String.class);
    }

    /**
     * 结构性护栏（外层门面）：真正的事务边界在这里。
     *
     * <p>调用链是 {@code PostsController → ArticleServiceImpl → ArticleQueryService}，
     * {@code ArticleServiceImpl} 是刻意的事务边界承载者。所以哪怕内层的
     * {@code ArticleQueryService} 已经去掉了 {@code @Transactional}，只要门面上还留着，
     * 连接照样被绑成一条 —— 这条断言守的就是这个最容易漏掉的点。
     */
    @Test
    @DisplayName("ArticleServiceImpl.getArticlePageVO 不得绑定事务（门面层才是真实事务边界）")
    void articleServiceFacade_mustNotBeTransactional() throws Exception {
        assertNotTransactional(pageMethod(ArticleServiceImpl.class),
                "ArticleServiceImpl.getArticlePageVO");
    }

    /**
     * 结构性护栏（内层查询服务）：防止有人"顺手"给内层再补一个 {@code @Transactional}。
     */
    @Test
    @DisplayName("ArticleQueryService.getArticlePageVO 不得绑定事务")
    void articleQueryService_mustNotBeTransactional() throws Exception {
        assertNotTransactional(pageMethod(ArticleQueryService.class),
                "ArticleQueryService.getArticlePageVO");
    }

    private static void assertNotTransactional(Method method, String label) {
        Transactional transactional =
                AnnotatedElementUtils.findMergedAnnotation(method, Transactional.class);

        assertThat(transactional)
                .as("""
                        %s 内部用 Mono.zip 并发执行两条富化查询。
                        一旦绑定事务，两条查询会共用同一条 R2DBC 连接，
                        撞上 r2dbc-mysql 的 RequestQueue（SPSC 队列）竞态，
                        抛 `Request queue is full` 并静默降级为空的数据。
                        如确需事务，必须把 buildAdminListVOs 里的 zip 改成串行（flatMap 链）。
                        """, label)
                .isNull();
    }

    /**
     * 行为回归：分页结果的作者名、分类名必须真正装配进去。
     *
     * <p>事故期间的表现不是报错，而是「页面能开、作者名是空的」——
     * 这条断言把那个静默失败钉成可见的失败。
     */
    @Test
    @DisplayName("getArticlePageVO 应装配出作者与分类信息")
    void getArticlePageVo_shouldPopulateAuthorAndCategory() {
        PostsRepository postsRepository = mock(PostsRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        FileService fileService = mock(FileService.class);
        PostMapper postMapper = mock(PostMapper.class);

        Posts post = new Posts();
        post.setId(1L);
        post.setUserId(7L);
        post.setTitle("Reactor 里的连接复用");

        User author = new User();
        author.setId(7L);
        author.setUsername("ryu");
        author.setNickname("阮磊");

        when(postsRepository.countPostsByCondition(
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(Mono.just(1L));
        when(postsRepository.findPostsByCondition(
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(0), eq(10)))
                .thenReturn(Flux.just(post));
        when(postsRepository.findPostsWithCategory(anyList()))
                .thenReturn(Flux.just(new PostCategoryProjection(1L, 2L, "技术")));
        when(userRepository.findByIdIn(anyList()))
                .thenReturn(Flux.just(author));

        when(postMapper.toAdminListVO(any(Posts.class), eq(2L), eq("技术"), eq("阮磊")))
                .thenAnswer(invocation -> {
                    PostAdminListVO vo = new PostAdminListVO();
                    vo.setId(invocation.getArgument(0, Posts.class).getId());
                    vo.setAuthorName(invocation.getArgument(3, String.class));
                    vo.setCategoryName(invocation.getArgument(2, String.class));
                    return vo;
                });

        ArticleViewAssembler assembler =
                new ArticleViewAssembler(postsRepository, userRepository, fileService, postMapper);
        ArticleQueryService service = new ArticleQueryService(
                postsRepository,
                mock(TagRepository.class),
                postMapper,
                mock(ContentService.class),
                mock(ArticleRelationService.class),
                assembler);

        Mono<PageResult<PostAdminListVO>> result =
                service.getArticlePageVO(0, 10, null, null, null, null, null, null);

        StepVerifier.create(result)
                .assertNext(page -> {
                    assertThat(page.getTotal()).isEqualTo(1L);
                    assertThat(page.getRecords()).hasSize(1);
                    PostAdminListVO vo = page.getRecords().get(0);
                    assertThat(vo.getAuthorName())
                            .as("作者名必须装上 —— 它为空就说明富化查询被静默吞掉了")
                            .isEqualTo("阮磊");
                    assertThat(vo.getCategoryName()).isEqualTo("技术");
                })
                .verifyComplete();
    }
}
