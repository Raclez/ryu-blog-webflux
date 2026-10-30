package com.ryu.blog.service.impl.article;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ryu.blog.dto.OperatorContext;
import com.ryu.blog.dto.PostCreateDTO;
import com.ryu.blog.dto.PostStickyDTO;
import com.ryu.blog.dto.PostStatusDTO;
import com.ryu.blog.dto.PostUpdateDTO;
import com.ryu.blog.entity.Posts;
import com.ryu.blog.enums.PublishMode;
import com.ryu.blog.exception.BusinessException;
import com.ryu.blog.mapper.PostMapper;
import com.ryu.blog.mapper.PostMapperImpl;
import com.ryu.blog.repository.PostCategoryRepository;
import com.ryu.blog.repository.PostsRepository;
import com.ryu.blog.service.ArticleVersionService;
import com.ryu.blog.service.ContentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文章「写路径」护栏：前端发什么，库里就得落什么。
 *
 * <p>这里的 JSON 是从 {@code ryu-admin/apps/web-ele/src/views/features/article/ArticleEditor.vue}
 * 里原样抄下来的请求体 —— 前端发 {@code 0/1} 而不是 {@code true/false}，
 * 发的是 {@code scheduleTime: "yyyy-MM-dd HH:mm:ss"} 而不是 ISO 串。
 * 这些都不是前端能单方面改的（改了就对不上后端的 {@code @JsonFormat}），
 * 所以后端必须能吃下这份**原样的**载荷。
 *
 * <p>断言的是「真正被 save 进库的那个实体」，而不是 DTO ——
 * DTO 里有字段不等于它落到了列上（{@code isSticky} 就是这么丢的）。
 *
 * @author ryu
 */
class ArticleCrudServiceWriteTest {

    /** 前端编辑器实际发出的请求体（创建）。 */
    private static final String FRONTEND_CREATE_JSON = """
            {
              "title": "前后端契约测试",
              "content": "正文",
              "excerpt": "摘要",
              "categoryId": 1,
              "tagsIds": [1, 2],
              "allowComment": 1,
              "seoTitle": "SEO 标题",
              "seoDescription": "SEO 描述",
              "isSticky": 1,
              "isOriginal": 1,
              "visibility": "private",
              "coverImageId": 5,
              "coverImageUrl": "http://localhost:5300/files/5.png",
              "password": "secret",
              "slug": "contract-test",
              "license": "MIT",
              "sort": 3,
              "sourceUrl": "https://example.com",
              "publishMode": "IMMEDIATE",
              "scheduleTime": null
            }
            """;

    private final PostsRepository postsRepository = mock(PostsRepository.class);
    private final PostCategoryRepository postCategoryRepository = mock(PostCategoryRepository.class);
    private final ArticleVersionService articleVersionService = mock(ArticleVersionService.class);
    private final ContentService contentService = mock(ContentService.class);
    private final ArticleRelationService relationService = mock(ArticleRelationService.class);

    /** 用 MapStruct 生成的真实实现 —— 映射逻辑本身就是被测对象之一。 */
    private final PostMapper postMapper = new PostMapperImpl();

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private ArticleCrudService service;

    @BeforeEach
    void setUp() {
        service = new ArticleCrudService(postsRepository, postCategoryRepository, articleVersionService,
                postMapper, contentService, objectMapper, relationService);

        when(postsRepository.save(any(Posts.class))).thenAnswer(inv -> {
            Posts p = inv.getArgument(0);
            p.setId(999L);
            return Mono.just(p);
        });
        when(postsRepository.countSlugUsage(anyString(), any()))
                .thenReturn(Mono.just(0L));
        when(relationService.addArticleCategory(anyLong(), anyLong())).thenReturn(Mono.empty());
        when(relationService.addArticleTags(anyLong(), any())).thenReturn(Mono.empty());
        when(relationService.removeAllArticleCategories(anyLong())).thenReturn(Mono.empty());
        when(articleVersionService.createVersion(any(Posts.class), any())).thenReturn(Mono.empty());
        when(contentService.generateExcerpt(any(), anyInt())).thenReturn(Mono.just("自动生成的摘要"));
    }

    @Test
    @DisplayName("创建文章：前端发来的可见性/置顶/原创/评论开关，全都要落到实体的列上")
    void createPersistsEverythingFrontendSends() throws Exception {
        PostCreateDTO dto = objectMapper.readValue(FRONTEND_CREATE_JSON, PostCreateDTO.class);

        StepVerifier.create(service.createArticle(dto, 7L))
                .expectNextCount(1)
                .verifyComplete();

        Posts saved = captureSavedEntity();

        // 可见性 —— 前台按这一列过滤，丢了就等于私密文章照样公开
        assertEquals("private", saved.getVisibility(), "visibility 必须落到实体上");
        // 访问密码存的是 BCrypt 哈希，不是明文
        assertNotEquals("secret", saved.getPassword(), "访问密码不能明文落库");
        assertTrue(ArticleAccessPasswordPolicy.matches("secret", saved.getPassword()),
                "哈希必须能被同一个口令验过");

        // 布尔列：前端发的是 0/1
        assertEquals(Boolean.TRUE, saved.getAllowComment(), "allowComment 应为 true");
        assertEquals(Boolean.TRUE, saved.getIsOriginal(), "isOriginal 应为 true");
        assertEquals(Boolean.TRUE, saved.getIsSticky(), "isSticky 应为 true —— 置顶开关必须真的落库");

        // 其余列
        assertEquals("前后端契约测试", saved.getTitle());
        assertEquals("摘要", saved.getExcerpt());
        assertEquals(5L, saved.getCoverImageId());
        assertEquals("contract-test", saved.getSlug());
        assertEquals("MIT", saved.getLicense());
        assertEquals(3, saved.getSort());
        assertEquals("https://example.com", saved.getSourceUrl());
        assertEquals(7L, saved.getUserId());

        // 发布方式：IMMEDIATE → 已发布
        assertEquals(Posts.Status.PUBLISHED, saved.getStatus());
    }

    @Test
    @DisplayName("创建文章：visibility 缺省时必须是 public，不能是 null")
    void createDefaultsVisibilityToPublic() throws Exception {
        PostCreateDTO dto = objectMapper.readValue(
                "{\"title\":\"没设访问权限\",\"content\":\"正文\"}", PostCreateDTO.class);

        StepVerifier.create(service.createArticle(dto, 7L))
                .expectNextCount(1)
                .verifyComplete();

        assertEquals(Posts.Visibility.PUBLIC, captureSavedEntity().getVisibility());
    }

    @Test
    @DisplayName("更新文章：只改标题，访问权限与置顶都不能被清掉")
    void updateKeepsUnmentionedFields() {
        Posts existing = existingArticle();

        PostUpdateDTO dto = new PostUpdateDTO();
        dto.setId(1L);
        dto.setTitle("新标题");

        StepVerifier.create(service.updateArticle(dto, new OperatorContext(7L, false)))
                .expectNextCount(1)
                .verifyComplete();

        assertEquals("新标题", existing.getTitle());
        assertEquals("private", existing.getVisibility(), "只改标题不该动访问权限");
        assertTrue(ArticleAccessPasswordPolicy.matches("secret", existing.getPassword()),
                "只改标题不该动访问密码");
        assertEquals(Boolean.TRUE, existing.getAllowComment());
        assertEquals(Boolean.TRUE, existing.getIsOriginal());
        assertEquals(Boolean.TRUE, existing.getIsSticky(), "只改标题不该动置顶");
    }

    @Test
    @DisplayName("更新文章：传了 publishMode=IMMEDIATE 时，已发布文章的发布时间不被重置")
    void updateWithImmediateKeepsPublishTime() {
        LocalDateTime publishedAt = LocalDateTime.now().minusDays(3);
        Posts existing = existingArticle();
        existing.setPublishTime(publishedAt);

        PostUpdateDTO dto = new PostUpdateDTO();
        dto.setId(1L);
        dto.setTitle("改个错别字");
        dto.setPublishMode(PublishMode.IMMEDIATE);

        StepVerifier.create(service.updateArticle(dto, new OperatorContext(7L, false)))
                .expectNextCount(1)
                .verifyComplete();

        assertEquals(publishedAt, existing.getPublishTime(), "改错别字不该重置首发日期");
        assertEquals(Posts.Status.PUBLISHED, existing.getStatus());
    }

    @Test
    @DisplayName("更新文章：publishMode 缺省时状态与发布时间都不动")
    void updateWithoutPublishModeKeepsState() {
        LocalDateTime publishedAt = LocalDateTime.now().minusDays(3);
        Posts existing = existingArticle();
        existing.setPublishTime(publishedAt);

        PostUpdateDTO dto = new PostUpdateDTO();
        dto.setId(1L);
        dto.setTitle("自动保存改的");

        StepVerifier.create(service.updateArticle(dto, new OperatorContext(7L, false)))
                .expectNextCount(1)
                .verifyComplete();

        assertEquals(Posts.Status.PUBLISHED, existing.getStatus(), "自动保存不该把文章打回草稿");
        assertEquals(publishedAt, existing.getPublishTime());
    }

    @Test
    @DisplayName("更新文章：把访问权限从 public 改成 private，必须真的改掉")
    void updateChangesVisibility() {
        Posts existing = existingArticle();
        existing.setVisibility("public");

        PostUpdateDTO dto = new PostUpdateDTO();
        dto.setId(1L);
        dto.setTitle("改成私密");
        dto.setVisibility("private");

        StepVerifier.create(service.updateArticle(dto, new OperatorContext(7L, false)))
                .expectNextCount(1)
                .verifyComplete();

        assertEquals("private", existing.getVisibility());
    }

    @Test
    @DisplayName("置顶：列表页一键切换后，置顶标记必须真的落库")
    void updateStickyPersistsTheFlag() {
        Posts existing = existingArticle();
        existing.setIsSticky(false);

        PostStickyDTO dto = new PostStickyDTO();
        dto.setId(1L);
        dto.setIsSticky(true);

        StepVerifier.create(service.updateSticky(dto, new OperatorContext(7L, false)))
                .expectNextCount(1)
                .verifyComplete();

        assertEquals(Boolean.TRUE, existing.getIsSticky(), "置顶开关必须真的写进实体");
    }

    @Test
    @DisplayName("置顶：取消置顶也要落库（false 不是「不修改」）")
    void updateStickyCanClearTheFlag() {
        Posts existing = existingArticle();
        existing.setIsSticky(true);

        PostStickyDTO dto = new PostStickyDTO();
        dto.setId(1L);
        dto.setIsSticky(false);

        StepVerifier.create(service.updateSticky(dto, new OperatorContext(7L, false)))
                .expectNextCount(1)
                .verifyComplete();

        assertEquals(Boolean.FALSE, existing.getIsSticky());
    }

    @Test
    @DisplayName("改发布状态不该顺手改掉置顶")
    void statusChangeKeepsSticky() {
        Posts existing = existingArticle();
        existing.setIsSticky(true);

        PostStatusDTO statusDTO = new PostStatusDTO();
        statusDTO.setId(1L);
        statusDTO.setStatus(Posts.Status.DRAFT);

        StepVerifier.create(service.updateArticleStatus(statusDTO, new OperatorContext(7L, false)))
                .expectNextCount(1)
                .verifyComplete();

        assertEquals(Boolean.TRUE, existing.getIsSticky(), "状态变更与置顶是两件事");
        assertEquals(Posts.Status.DRAFT, existing.getStatus());
    }

    @Test
    @DisplayName("更新文章：没填访问密码时不能把原来的密码抹掉")
    void updateKeepsPasswordWhenNotProvided() {
        Posts existing = existingArticle();
        String before = existing.getPassword();

        PostUpdateDTO dto = new PostUpdateDTO();
        dto.setId(1L);
        dto.setTitle("改个标题");
        // 详情接口刻意不返回明文密码 → 编辑器回显出来的一定是空的
        dto.setPassword("");

        StepVerifier.create(service.updateArticle(dto, new OperatorContext(7L, false)))
                .expectNextCount(1)
                .verifyComplete();

        assertEquals(before, existing.getPassword(),
                "没给新密码就不能覆盖 —— 空串会把密码保护的文章直接解锁");
        assertTrue(ArticleAccessPasswordPolicy.matches("secret", existing.getPassword()),
                "原来的口令必须照样能打开文章");
    }

    @Test
    @DisplayName("更新文章：给了新密码就换成新密码（且存的是哈希）")
    void updateChangesPasswordWhenProvided() {
        Posts existing = existingArticle();

        PostUpdateDTO dto = new PostUpdateDTO();
        dto.setId(1L);
        dto.setTitle("换个密码");
        dto.setPassword("new-secret");

        StepVerifier.create(service.updateArticle(dto, new OperatorContext(7L, false)))
                .expectNextCount(1)
                .verifyComplete();

        assertNotEquals("new-secret", existing.getPassword(), "访问密码不能明文落库");
        assertTrue(ArticleAccessPasswordPolicy.matches("new-secret", existing.getPassword()));
        assertFalse(ArticleAccessPasswordPolicy.matches("secret", existing.getPassword()),
                "换了密码之后旧口令必须失效");
    }

    @Test
    @DisplayName("更新文章：历史明文访问密码会被顺手洗成哈希（口令不变）")
    void updateRehashesLegacyPlaintextPassword() {
        Posts existing = existingArticle();
        // 2026-10-30 之前，访问密码是明文存的
        existing.setPassword("secret");

        PostUpdateDTO dto = new PostUpdateDTO();
        dto.setId(1L);
        dto.setTitle("改个标题");

        StepVerifier.create(service.updateArticle(dto, new OperatorContext(7L, false)))
                .expectNextCount(1)
                .verifyComplete();

        assertNotEquals("secret", existing.getPassword(), "历史明文不能继续留在库里");
        assertTrue(ArticleAccessPasswordPolicy.matches("secret", existing.getPassword()),
                "洗成哈希之后口令本身不能变");
    }

    @Test
    @DisplayName("创建文章：校验别名时 excludeId 必须是 null（派生查询会拒收 null 参数）")
    void createChecksSlugWithNullExcludeId() throws Exception {
        PostCreateDTO dto = objectMapper.readValue(
                "{\"title\":\"带别名的文章\",\"content\":\"正文\",\"slug\":\"my-slug\"}", PostCreateDTO.class);

        StepVerifier.create(service.createArticle(dto, 7L))
                .expectNextCount(1)
                .verifyComplete();

        // excludeId 传 null 是「创建」的语义。原先用的是派生查询
        // countBySlugAndIsDeletedAndIdNot(...)，而 Spring Data R2DBC 的派生查询
        // 会拒绝 null 参数 → 任何带别名的创建请求都抛
        // "Value of parameter with name id must not be null"。
        verify(postsRepository).countSlugUsage(eq("my-slug"), isNull());
        assertEquals("my-slug", captureSavedEntity().getSlug());
    }

    @Test
    @DisplayName("创建文章：别名已被占用时必须报错，而不是把重复别名写进库")
    void createRejectsDuplicateSlug() throws Exception {
        when(postsRepository.countSlugUsage(anyString(), any())).thenReturn(Mono.just(1L));

        // save() 返回的是惰性 Mono —— 校验失败时它**不会被订阅**，
        // 所以断言的是「有没有真的写库」，而不是「方法有没有被调用」。
        AtomicBoolean saved = new AtomicBoolean(false);
        when(postsRepository.save(any(Posts.class))).thenReturn(Mono.defer(() -> {
            saved.set(true);
            Posts p = new Posts();
            p.setId(1L);
            return Mono.just(p);
        }));

        PostCreateDTO dto = objectMapper.readValue(
                "{\"title\":\"重复别名\",\"content\":\"正文\",\"slug\":\"taken\"}", PostCreateDTO.class);

        StepVerifier.create(service.createArticle(dto, 7L))
                .expectError(BusinessException.class)
                .verify();

        assertFalse(saved.get(), "别名冲突时不能真的写库");
    }

    @Test
    @DisplayName("创建文章：别名是空串时存成 null（slug 上有唯一键，空串会撞键）")
    void createTreatsBlankSlugAsNull() throws Exception {
        PostCreateDTO dto = objectMapper.readValue(
                "{\"title\":\"空别名\",\"content\":\"正文\",\"slug\":\"\"}", PostCreateDTO.class);

        StepVerifier.create(service.createArticle(dto, 7L))
                .expectNextCount(1)
                .verifyComplete();

        assertNull(captureSavedEntity().getSlug(), "空别名必须存成 null");
        verify(postsRepository, never()).countSlugUsage(anyString(), any());
    }

    @Test
    @DisplayName("DTO 里有的字段，实体里也得有对应属性 —— 否则就是静默丢弃")
    void dtoFieldsHaveEntityProperties() {        List<String> dtoFields = List.of("title", "content", "excerpt", "coverImageId", "slug",
                "isSticky", "isOriginal", "sort", "allowComment", "license", "visibility",
                "sourceUrl", "password");

        for (String field : dtoFields) {
            if (!hasEntityProperty(field)) {
                fail("Posts 实体没有 " + field + " 属性 —— DTO 里有它，但保存时会静默丢掉"
                        + "（前端会以为设置成功了）");
            }
        }
    }

    private Posts existingArticle() {
        Posts existing = new Posts();
        existing.setId(1L);
        existing.setUserId(7L);
        existing.setTitle("旧标题");
        existing.setContent("旧正文");
        existing.setVisibility("private");
        // 2026-10-30 起，访问密码存的是 BCrypt 哈希（历史上是明文）
        existing.setPassword(ArticleAccessPasswordPolicy.encode("secret"));
        existing.setAllowComment(true);
        existing.setIsOriginal(true);
        existing.setIsSticky(true);
        existing.setStatus(Posts.Status.PUBLISHED);
        when(postsRepository.findById(1L)).thenReturn(Mono.just(existing));
        return existing;
    }

    private Posts captureSavedEntity() {
        ArgumentCaptor<Posts> captor = ArgumentCaptor.forClass(Posts.class);
        verify(postsRepository).save(captor.capture());
        return captor.getValue();
    }

    private boolean hasEntityProperty(String name) {
        for (Field f : Posts.class.getDeclaredFields()) {
            if (f.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
