package com.ryu.blog.config;

import com.ryu.blog.constant.CacheConstants;
import com.ryu.blog.service.impl.ArticleServiceImpl;
import com.ryu.blog.service.impl.FileServiceImpl;
import com.ryu.blog.strategy.impl.MinioStorageStrategy;
import com.ryu.blog.utils.CacheKeyUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.cache.annotation.CachePut;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 缓存注解的结构性护栏。
 *
 * <p>下面这几类 bug 都属于「写错了也不会报错、只会静默地缓存错数据」的类型，
 * 靠人工 review 很难每次都拦住，所以用扫描代码的方式固化成断言：
 *
 * <ol>
 *   <li><b>通配符驱逐</b>：Spring 的 {@code @CacheEvict} <b>不支持 {@code *} 通配符</b>。
 *       写 {@code key = "'tag:page:*'"} 只会去删一个<b>字面量名叫 {@code tag:page:*}</b>
 *       的 key —— 什么都没删，缓存永远不过期。</li>
 *   <li><b>集合哈希做 key</b>：{@code List.hashCode()} 是「逐元素 hash 相加」，
 *       不同批次很容易撞出同一个值，撞上就把<b>另一批</b>的数据返回给调用方。</li>
 *   <li><b>缓存名没注册</b>：{@code @Cacheable(cacheNames = "xxx")} 里的名字
 *       没在 {@link CacheConfig} 注册过时，要么报错，要么悄悄落到默认 TTL 的动态缓存里。</li>
 * </ol>
 */
class CacheAnnotationGuardTest {

    private static final String[] ANNOTATIONS = {
            "org.springframework.cache.annotation.Cacheable",
            "org.springframework.cache.annotation.CacheEvict",
            "org.springframework.cache.annotation.CachePut"
    };

    /**
     * 扫出项目里所有带缓存注解的方法。
     */
    private static List<Method> cachedMethods() throws IOException {
        List<Method> methods = new ArrayList<>();
        for (Class<?> clazz : projectClasses()) {
            Method[] declared;
            try {
                declared = clazz.getDeclaredMethods();
            } catch (Throwable ignored) {
                continue;
            }
            for (Method method : declared) {
                if (hasCacheAnnotation(method)) {
                    methods.add(method);
                }
            }
        }
        return methods;
    }

    private static boolean hasCacheAnnotation(Method method) {
        return AnnotatedElementUtils.hasAnnotation(method, Cacheable.class)
                || AnnotatedElementUtils.hasAnnotation(method, CacheEvict.class)
                || AnnotatedElementUtils.hasAnnotation(method, CachePut.class)
                || AnnotatedElementUtils.hasAnnotation(method, Caching.class);
    }

    private static List<Class<?>> projectClasses() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath*:com/ryu/blog/**/*.class");
        List<Class<?>> classes = new ArrayList<>();
        for (Resource resource : resources) {
            String path;
            try {
                path = resource.getURL().getPath();
            } catch (IOException e) {
                continue;
            }
            int idx = path.indexOf("com/ryu/blog/");
            if (idx < 0) {
                continue;
            }
            String className = path.substring(idx, path.length() - ".class".length()).replace('/', '.');
            try {
                classes.add(Class.forName(className, false, CacheAnnotationGuardTest.class.getClassLoader()));
            } catch (Throwable ignored) {
                // 加载不了的类跳过（可选依赖缺失等）
            }
        }
        return classes;
    }

    /**
     * 取出方法上所有「驱逐类」缓存操作（{@code @CacheEvict}）。
     */
    private static List<CacheEvict> evictOperations(Method method) {
        List<CacheEvict> evicts = new ArrayList<>();
        CacheEvict direct = AnnotatedElementUtils.findMergedAnnotation(method, CacheEvict.class);
        if (direct != null) {
            evicts.add(direct);
        }
        Caching caching = AnnotatedElementUtils.findMergedAnnotation(method, Caching.class);
        if (caching != null) {
            evicts.addAll(Arrays.asList(caching.evict()));
        }
        return evicts;
    }

    private static List<Cacheable> cacheableOperations(Method method) {
        List<Cacheable> cacheables = new ArrayList<>();
        Cacheable direct = AnnotatedElementUtils.findMergedAnnotation(method, Cacheable.class);
        if (direct != null) {
            cacheables.add(direct);
        }
        Caching caching = AnnotatedElementUtils.findMergedAnnotation(method, Caching.class);
        if (caching != null) {
            cacheables.addAll(Arrays.asList(caching.cacheable()));
        }
        return cacheables;
    }

    @Test
    @DisplayName("缓存驱逐不得依赖 '*' 通配符 —— Spring 不支持，等于什么都没删")
    void cacheEvict_mustNotUseWildcardKey() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Method method : cachedMethods()) {
            for (CacheEvict evict : evictOperations(method)) {
                String key = evict.key();
                // allEntries = true 时 key 会被忽略，不算问题
                if (evict.allEntries() || key == null || !key.contains("*")) {
                    continue;
                }
                violations.add(method.getDeclaringClass().getSimpleName() + "#" + method.getName()
                        + " → key = \"" + key + "\"");
            }
        }

        assertThat(violations)
                .as("""
                        这些 @CacheEvict 用了 '*' 通配符，但 Spring 的缓存驱逐**不支持通配符**：
                        它会去找一个字面量名叫该字符串的 key，等于什么都没删，缓存永远不过期。
                        正确做法：用 allEntries = true，或者把缓存拆成独立的 cacheNames 再精确驱逐。
                        """)
                .isEmpty();
    }

    @Test
    @DisplayName("缓存 key 不得用集合的 hashCode —— 会撞键并串数据")
    void cacheKey_mustNotUseCollectionHashCode() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Method method : cachedMethods()) {
            for (Cacheable cacheable : cacheableOperations(method)) {
                String key = cacheable.key();
                if (key == null || !key.contains("hashCode()")) {
                    continue;
                }
                violations.add(method.getDeclaringClass().getSimpleName() + "#" + method.getName()
                        + " → key = \"" + key + "\"");
            }
        }

        assertThat(violations)
                .as("""
                        List.hashCode() 是「逐元素 hash 相加」，不同批次很容易撞出同一个值
                        （例如 [1,2] 与 [3,0]）。撞上时会把**另一批**的数据返回给调用方。
                        请改用 CacheKeyUtils.ofIds(...)。
                        """)
                .isEmpty();
    }

    /**
     * 缓存注解的 {@code key} 表达式必须是<b>合法 SpEL</b>。
     *
     * <p>SpEL 是在<b>第一次调用方法时</b>才解析的，写错了编译期完全无感，
     * 只会在线上/联调时抛 {@code SpelParseException} —— 整条接口直接 500。
     * 典型错法：拼多个字面量时把冒号放到引号外面，
     * {@code "... + #fileId" + ":' + T(..).xxx()"} 拼出 {@code 'info:' + #fileId:' + T(..)}，
     * 解析器在 {@code #fileId:} 处就报「still more data in the expression: 'colon(:)'」。
     */
    @Test
    @DisplayName("缓存注解的 key 表达式必须能解析 —— 写错只在运行时炸")
    void cacheKeySpel_mustParse() throws IOException {
        SpelExpressionParser parser = new SpelExpressionParser();
        List<String> violations = new ArrayList<>();
        for (Method method : cachedMethods()) {
            for (String key : cacheKeysOf(method)) {
                if (key == null || key.isBlank()) {
                    // 用 keyGenerator 或默认 key 的情况，没有表达式可解析
                    continue;
                }
                try {
                    parser.parseExpression(key);
                } catch (RuntimeException e) {
                    violations.add(method.getDeclaringClass().getSimpleName() + "#" + method.getName()
                            + " → key = \"" + key + "\"  (" + e.getMessage() + ")");
                }
            }
        }

        assertThat(violations)
                .as("""
                        这些缓存 key 表达式解析不了。SpEL 直到方法第一次被调用才解析，
                        所以编译期和启动期都不会报错，线上表现为该接口直接 500。
                        拼接多个字面量时，冒号必须写在引号内：+ " + ':' + #x"，
                        而不是 + ":' + #x"。
                        """)
                .isEmpty();
    }

    /**
     * 缓存 key 里不得出现 {@code #this}。
     *
     * <p>SpEL 的 {@code #this} 指向<b>根对象</b>，而缓存表达式的根对象是
     * {@code CacheExpressionRootObject}，不是被拦截的目标 bean。
     * 想调目标 bean 的方法只能走 {@code #root.target}。
     * 写成 {@code #this.getStrategyKey()} 解析得动，第一次调用就抛
     * {@code EL1004E: Method getStrategyKey() cannot be found}。
     */
    @Test
    @DisplayName("缓存 key 不得用 #this —— 它指向缓存根对象，不是目标 bean")
    void cacheKeySpel_mustNotReferenceThis() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Method method : cachedMethods()) {
            for (String key : cacheKeysOf(method)) {
                if (key == null || !key.contains("#this")) {
                    continue;
                }
                violations.add(method.getDeclaringClass().getSimpleName() + "#" + method.getName()
                        + " → key = \"" + key + "\"");
            }
        }

        assertThat(violations)
                .as("""
                        缓存 key 里用了 #this。SpEL 的 #this 是根对象 CacheExpressionRootObject，
                        不是目标 bean —— 调 bean 方法会抛 EL1004E。请改用 #root.target。
                        """)
                .isEmpty();
    }

    /**
     * 存储策略的客户端缓存 key 必须<b>真的能求值</b>出正确结果。
     *
     * <p>只校验「能解析」不够：{@code #this.getStrategyKey()} 解析得动，
     * 但求值时目标 bean 换了对象，直接 500。
     */
    @Test
    @DisplayName("存储策略客户端缓存 key 必须能对缓存根对象求值出 client:minio / bucket:minio")
    void storageClientCacheKeys_mustEvaluateAgainstCacheRoot() throws Exception {
        Method getMinioClient = MinioStorageStrategy.class.getMethod("getMinioClient");
        Method getBucketName = MinioStorageStrategy.class.getMethod("getBucketName");

        assertThat(evaluateCacheKey(getMinioClient))
                .as("MinIO 客户端缓存 key 求值失败 —— 见 cacheKeySpel_mustNotReferenceThis 的说明")
                .isEqualTo("client:minio");
        assertThat(evaluateCacheKey(getBucketName))
                .as("MinIO 存储桶缓存 key 求值失败 —— 见 cacheKeySpel_mustNotReferenceThis 的说明")
                .isEqualTo("bucket:minio");
    }

    private static String evaluateCacheKey(Method method) {
        Cacheable cacheable = AnnotatedElementUtils.findMergedAnnotation(method, Cacheable.class);
        assertThat(cacheable)
                .as(method.getName() + " 必须有 @Cacheable")
                .isNotNull();
        StandardEvaluationContext context = new StandardEvaluationContext(new CacheRootStub(method));
        return new SpelExpressionParser().parseExpression(cacheable.key()).getValue(context, String.class);
    }

    /**
     * 模拟 Spring 的 {@code CacheExpressionRootObject} —— 那个类本身是包私有的，
     * 测试里只能照它的 getter 复刻一个。
     */
    public static class CacheRootStub {
        private final Method method;

        CacheRootStub(Method method) {
            this.method = method;
        }

        public Object getTarget() {
            return new TargetStub();
        }

        public Method getMethod() {
            return method;
        }

        public String getMethodName() {
            return method.getName();
        }

        public Object[] getArgs() {
            return new Object[0];
        }

        public Class<?> getTargetClass() {
            return TargetStub.class;
        }
    }

    /** 目标 bean 的替身：只需要 getStrategyKey()。 */
    public static class TargetStub {
        public String getStrategyKey() {
            return "minio";
        }
    }

    @Test
    @DisplayName("注解里用到的缓存名必须在 CacheConfig 里注册")
    void everyReferencedCacheName_mustBeRegistered() throws IOException {
        Collection<String> registered = new CacheConfig().cacheManager().getCacheNames();
        assertThat(registered)
                .as("缓存名注册表本身就不该是空的 —— 否则这条断言没有意义")
                .contains(CacheConstants.POST_CACHE);

        Set<String> violations = new LinkedHashSet<>();
        for (Method method : cachedMethods()) {
            for (String name : cacheNamesOf(method)) {
                if (!registered.contains(name)) {
                    violations.add(name + "  (" + method.getDeclaringClass().getSimpleName()
                            + "#" + method.getName() + ")");
                }
            }
        }

        assertThat(violations)
                .as("""
                        这些缓存名没在 CacheConfig#registerCustomCaches 里注册。
                        CaffeineCacheManager 在未固定 cacheNames 时会**动态创建**同名缓存，
                        于是它们悄悄用上默认 TTL（30 分钟）与默认容量（500），
                        与 CacheConfig 里精心配置的 TTL 完全不符 —— 配置形同虚设。
                        """)
                .isEmpty();
    }

    /**
     * 收集方法（含类级 {@code @CacheConfig}）实际会用到的全部缓存名。
     */
    private static Set<String> cacheNamesOf(Method method) {
        Set<String> names = new LinkedHashSet<>();

        org.springframework.cache.annotation.CacheConfig classConfig = AnnotatedElementUtils.findMergedAnnotation(
                method.getDeclaringClass(), org.springframework.cache.annotation.CacheConfig.class);
        if (classConfig != null) {
            names.addAll(Arrays.asList(classConfig.cacheNames()));
        }

        for (Cacheable cacheable : cacheableOperations(method)) {
            names.addAll(resolveNames(cacheable.cacheNames(), cacheable.value()));
        }
        for (CacheEvict evict : evictOperations(method)) {
            names.addAll(resolveNames(evict.cacheNames(), evict.value()));
        }
        CachePut put = AnnotatedElementUtils.findMergedAnnotation(method, CachePut.class);
        if (put != null) {
            names.addAll(resolveNames(put.cacheNames(), put.value()));
        }
        return names;
    }

    /**
     * 收集方法上所有显式写出的 {@code key} 表达式（未写 key 的方法返回空串，调用方自行过滤）。
     */
    private static List<String> cacheKeysOf(Method method) {
        List<String> keys = new ArrayList<>();
        for (Cacheable cacheable : cacheableOperations(method)) {
            keys.add(cacheable.key());
        }
        for (CacheEvict evict : evictOperations(method)) {
            keys.add(evict.key());
        }
        CachePut put = AnnotatedElementUtils.findMergedAnnotation(method, CachePut.class);
        if (put != null) {
            keys.add(put.key());
        }
        Caching caching = AnnotatedElementUtils.findMergedAnnotation(method, Caching.class);
        if (caching != null) {
            for (CachePut nestedPut : caching.put()) {
                keys.add(nestedPut.key());
            }
        }
        return keys;
    }

    private static Set<String> resolveNames(String[] cacheNames, String[] value) {
        Set<String> names = new LinkedHashSet<>();
        // Spring 的运行时语义：cacheNames 为空时回落到 value
        String[] effective = (cacheNames != null && cacheNames.length > 0) ? cacheNames : value;
        if (effective != null) {
            names.addAll(Arrays.asList(effective));
        }
        return names;
    }

    /**
     * 文章详情的缓存 key <b>必须</b>包含当前用户身份。
     *
     * <p>文章可见性是「未公开的文章只允许作者本人或管理员读」。缓存命中时方法体根本不会执行，
     * 可见性校验也就被跳过了 —— key 里只有文章 ID 的话，作者缓存下来的草稿
     * 会被匿名访客直接读走。
     *
     * <p>可见性判定有<b>两项</b>输入：用户 ID 与「能否管理他人」。
     * 只带用户 ID 的话，管理员读到的草稿详情会被缓存下来发给「同 ID 的普通用户」。
     */
    @Test
    @DisplayName("文章详情缓存 key 必须带上当前用户身份与管理员标记")
    void articleDetailCacheKey_mustDependOnCurrentUser() throws Exception {
        Method method = ArticleServiceImpl.class.getMethod("getArticleDetailVO", Long.class);
        Cacheable cacheable = AnnotatedElementUtils.findMergedAnnotation(method, Cacheable.class);

        assertThat(cacheable)
                .as("getArticleDetailVO 必须有 @Cacheable")
                .isNotNull();
        assertThat(cacheable.key())
                .as("""
                        文章详情缓存 key 里没有当前用户身份。
                        命中缓存会跳过方法体内的可见性校验，私有文章会泄漏给匿名访客。
                        """)
                .contains("currentUserIdOrNull");
        assertThat(cacheable.key())
                .as("""
                        文章详情缓存 key 里没有「能否管理他人」的标记。
                        可见性判定是两项输入（用户 ID + 管理标记），key 只带一项就会串数据：
                        管理员读到的草稿详情会被缓存下来发给同 ID 的普通用户。
                        """)
                .contains("currentCanManageOthers");
    }

    /**
     * 文件相关缓存的 key <b>必须</b>包含当前用户身份与管理员标记。
     *
     * <p>文件的可见性是「公开文件人人可读、私有文件只有主人与管理员可读」。
     * 缓存命中时方法体根本不会执行，可见性校验也就被跳过了。
     *
     * <p>可见性判定有<b>两项</b>输入：用户 ID 与「能否管理他人」，两项都要进 key。
     * 只带一项的典型后果：
     * <ul>
     *   <li>不带用户 ID → A 的私有文件详情被缓存下来发给 B；</li>
     *   <li>不带管理员标记 → 管理员读到的全量列表被缓存下来发给普通用户。</li>
     * </ul>
     */
    @Test
    @DisplayName("文件缓存 key 必须带上当前用户身份与管理员标记")
    void fileCacheKeys_mustDependOnCurrentUser() throws Exception {
        List<String> cachedFileMethods = List.of(
                "getFileInfo", "getFileVersions", "getBatchFileInfos",
                "getBatchFileUrls", "getUserFiles", "getFilesByType");

        for (String name : cachedFileMethods) {
            Method method = findFileServiceMethod(name);
            Cacheable cacheable = AnnotatedElementUtils.findMergedAnnotation(method, Cacheable.class);

            assertThat(cacheable)
                    .as(name + " 必须有 @Cacheable")
                    .isNotNull();
            assertThat(cacheable.key())
                    .as(name + " 的缓存 key 里没有当前用户身份 —— 命中缓存会跳过可见性校验，私有文件会泄漏")
                    .contains("currentUserIdOrNull");
            assertThat(cacheable.key())
                    .as(name + " 的缓存 key 里没有「能否管理他人」的标记 —— 可见性判定是两项输入，"
                            + "key 只带一项就会串数据：管理员读到的列表会被缓存下来发给普通用户")
                    .contains("currentCanManageOthers");
        }
    }

    /**
     * 按方法名在文件服务门面里找方法（参数类型各不相同，逐个按名字找最省事）
     */
    private static Method findFileServiceMethod(String name) throws NoSuchMethodException {
        for (Method method : FileServiceImpl.class.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        throw new NoSuchMethodException("FileServiceImpl 上找不到方法: " + name);
    }

    @Test
    @DisplayName("CacheKeyUtils.ofIds：无碰撞，且与入参顺序无关")
    void cacheKeyUtils_ofIds_isCollisionFreeAndOrderInsensitive() {
        // List.hashCode() 的经典碰撞：[1, 31] 与 [2, 0]
        //   List.hashCode = 31*(31*1 + 31) + 0 = 1023
        //   List.hashCode = 31*(31*2 +  0) + 0 = 1023
        assertThat(List.of(1L, 31L).hashCode())
                .as("前提：[1,31] 与 [2,0] 的 hashCode 确实相同")
                .isEqualTo(List.of(2L, 0L).hashCode());

        assertThat(CacheKeyUtils.ofIds(List.of(1L, 31L)))
                .as("碰撞的两组 ID 必须算出不同的 key")
                .isNotEqualTo(CacheKeyUtils.ofIds(List.of(2L, 0L)));

        assertThat(CacheKeyUtils.ofIds(List.of(2L, 1L)))
                .as("顺序不同的同一批 ID 应共用一个 key")
                .isEqualTo(CacheKeyUtils.ofIds(List.of(1L, 2L)));

        assertThat(CacheKeyUtils.ofIds(null)).isEmpty();
        assertThat(CacheKeyUtils.ofIds(List.of())).isEmpty();
    }
}
