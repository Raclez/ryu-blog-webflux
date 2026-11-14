package com.ryu.blog.service.impl.file;

import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import com.ryu.blog.config.SaTokenConfig;
import com.ryu.blog.controller.FileController;
import com.ryu.blog.entity.File;
import com.ryu.blog.exception.PermissionDeniedException;
import com.ryu.blog.exception.ResourceNotFoundException;
import com.ryu.blog.repository.FileRepository;
import com.ryu.blog.repository.UserRepository;
import com.ryu.blog.strategy.FileStorageStrategy;
import com.ryu.blog.strategy.StorageConfigManager;
import com.ryu.blog.strategy.StorageStrategyRegistry;
import com.ryu.blog.strategy.impl.LocalStorageStrategy;
import com.ryu.blog.vo.FileDownloadVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.web.bind.annotation.GetMapping;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 公开文件 URL 契约测试。
 *
 * <p><b>它守的是什么</b>：文件模块曾经有<b>四套互不兼容</b>的 URL 模型，
 * 而实际返回给前端的那套在本地存储下是<b>死链</b>：
 * <ul>
 *   <li>{@code AbstractFileStorageStrategy.getPublicUrl} 兜底到 {@code /api/files/public/**}
 *       —— 项目根本没有 {@code /api} 前缀（{@code webflux.base-path: /}），这个路由不存在；</li>
 *   <li>{@code LocalStorageStrategy.buildAccessUrlAsync} 兜底到 {@code /files/**}
 *       —— 同样不存在，而且撞在 {@code /files/{id}} 这套按 ID 的 API 命名空间里；</li>
 *   <li>按 ID 的路由（{@code /files/preview/{id}}）每次访问都要 {@code system:file:view}
 *       权限 + {@code Authorization} 头，而 {@code <img src>} 带不了头；</li>
 *   <li>{@code getBatchFilePermanentUrls} 是一条<b>不过可见性判定</b>的后门 ——
 *       私有文件的存储路径会原样发出去。</li>
 * </ul>
 *
 * <p>本测试把「公开 URL 只有一条路、且这条路只服务公开文件」钉死。
 *
 * @author ryu
 */
class FilePublicUrlContractTest {

    private static final String PUBLIC_PATH = "images/2026/10/30/abc.png";
    private static final byte[] CONTENT = "public-bytes".getBytes(StandardCharsets.UTF_8);

    private StorageConfigManager configManager;
    private FileRepository fileRepository;
    private StorageStrategyRegistry strategyRegistry;
    private FileDownloadService downloadService;
    private FileMetadataService metadataService;

    @BeforeEach
    void setUp() {
        configManager = mock(StorageConfigManager.class);
        when(configManager.getAccessUrlAsync(anyString())).thenReturn(Mono.empty());
        when(configManager.getConfigPropertyAsync(anyString(), anyString(), any()))
                .thenAnswer(inv -> {
                    String defaultValue = inv.getArgument(2);
                    return Mono.just(defaultValue == null ? "" : defaultValue);
                });

        fileRepository = mock(FileRepository.class);
        UserRepository userRepository = mock(UserRepository.class);

        FileStorageStrategy strategy = mock(FileStorageStrategy.class);
        when(strategy.getStrategyKey()).thenReturn("local");
        when(strategy.getPublicUrl(anyString()))
                .thenAnswer(inv -> Mono.just("/files/public/" + inv.getArgument(0)));

        strategyRegistry = mock(StorageStrategyRegistry.class);
        when(strategyRegistry.getStrategy(anyString())).thenReturn(Mono.just(strategy));

        downloadService = new FileDownloadService(fileRepository, strategyRegistry);
        metadataService = new FileMetadataService(fileRepository, userRepository, strategyRegistry, downloadService);
    }

    // ==================================================================
    // 1. getPublicUrl 的兜底必须指向真实存在的路由
    // ==================================================================

    @Test
    @DisplayName("getPublicUrl：没有配置访问地址时兜底到 /files/public/**（不是不存在的 /api/files/public）")
    void publicUrl_fallsBackToApplicationRoute() {
        when(configManager.getAccessUrlAsync(anyString())).thenReturn(Mono.empty());
        LocalStorageStrategy strategy = new LocalStorageStrategy(configManager);

        String url = strategy.getPublicUrl(PUBLIC_PATH).block();

        assertNotNull(url);
        assertTrue(url.startsWith("/files/public/"),
                "兜底 URL 必须指向应用内真实存在的公开路由，实际: " + url);
        assertFalse(url.contains("/api/"),
                "项目没有 /api 前缀（webflux.base-path: /），带 /api 的 URL 一定打不开，实际: " + url);
    }

    @Test
    @DisplayName("getPublicUrl：访问地址配成空串时同样兜底，不能产出裸路径")
    void publicUrl_blankAccessUrl_alsoFallsBack() {
        when(configManager.getAccessUrlAsync(anyString())).thenReturn(Mono.just(""));
        LocalStorageStrategy strategy = new LocalStorageStrategy(configManager);

        String url = strategy.getPublicUrl(PUBLIC_PATH).block();

        assertNotNull(url);
        assertTrue(url.startsWith("/files/public/"),
                "空串访问地址等价于「没配」，必须走兜底路由，实际: " + url);
    }

    @Test
    @DisplayName("本地策略的预览 URL 兜底到 /files/public/**，不再用 /files/ 前缀撞 API 命名空间")
    void localPreviewUrl_usesPublicRoute() {
        when(configManager.getConfigPropertyAsync(anyString(), anyString(), any()))
                .thenAnswer(inv -> {
                    String key = inv.getArgument(1);
                    String defaultValue = inv.getArgument(2);
                    if ("accessUrl".equals(key)) {
                        return Mono.just("");
                    }
                    return Mono.just(defaultValue == null ? "" : defaultValue);
                });
        LocalStorageStrategy strategy = new LocalStorageStrategy(configManager);

        String url = strategy.generatePreviewUrl(PUBLIC_PATH, 3600).block();

        assertNotNull(url);
        assertTrue(url.startsWith("/files/public/"),
                "本地存储没有可直链的公开地址，必须兜底到应用内公开路由，实际: " + url);
    }

    // ==================================================================
    // 2. 批量永久 URL：私有文件不得出现在结果里
    // ==================================================================

    @Test
    @DisplayName("批量永久 URL：所有文件都返回（可见性已下线，不再有私有文件）")
    void permanentUrls_returnsEveryFile() {
        File publicFile = file(1L, PUBLIC_PATH, 100L);
        File privateFile = file(2L, "documents/2026/10/30/secret.pdf", 200L);
        when(fileRepository.findByIdAndIsDeleted(anyLong(), anyInt()))
                .thenAnswer(inv -> {
                    Long id = inv.getArgument(0);
                    if (publicFile.getId().equals(id)) {
                        return Mono.just(publicFile);
                    }
                    if (privateFile.getId().equals(id)) {
                        return Mono.just(privateFile);
                    }
                    return Mono.empty();
                });

        Map<Long, String> urls = metadataService.getBatchFilePermanentUrls(List.of(1L, 2L)).block();

        assertNotNull(urls);
        assertTrue(urls.containsKey(1L), "文件应当拿到永久 URL");
        assertTrue(urls.containsKey(2L),
                "文件可见性下线后不存在「私有文件不返回」这条规则 —— 所有文件都按公开处理");
    }

    // ==================================================================
    // 3. 公开路由：存在、匿名可达、且只服务公开文件
    // ==================================================================

    @Test
    @DisplayName("公开路由存在于 FileController 上")
    void publicRoute_exists() {
        boolean found = false;
        for (Method method : FileController.class.getDeclaredMethods()) {
            GetMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, GetMapping.class);
            if (mapping != null && List.of(mapping.value()).contains("/public/{*path}")) {
                found = true;
                break;
            }
        }
        assertTrue(found,
                "FileController 上必须有一个 GET /files/public/{*path} 路由 —— "
                        + "没有它，getPublicUrl 的兜底 URL 指向的就是死链");
    }

    @Test
    @DisplayName("公开路由必须在 Sa-Token 白名单里（<img src> 带不了 Authorization 头）")
    void publicRoute_isAnonymous() {
        // 公开路径现在来自配置（blog.auth.public-paths），白名单只有一个出处 ——
        // 这里手工把这条路径喂进去，等价于配置文件里写了它。
        com.ryu.blog.config.AuthProperties properties = new com.ryu.blog.config.AuthProperties();
        properties.setPublicPaths(java.util.List.of("/files/public/**"));
        SaReactorFilter filter = new SaTokenConfig(properties).saReactorFilter();

        assertTrue(filter.excludeList.contains("/files/public/**"),
                "公开文件直链必须匿名可访问，否则 <img src> 取不到图，实际白名单: " + filter.excludeList);
    }

    @Test
    @DisplayName("按路径取文件：匿名可达，不再区分公开/私有；不存在仍 404")
    void publicAccess_servesEveryFile() {
        File publicFile = file(1L, PUBLIC_PATH, 100L);
        File privateFile = file(2L, "documents/2026/10/30/secret.pdf", 200L);
        when(fileRepository.findByFilePathAndIsDeleted(anyString(), anyInt()))
                .thenAnswer(inv -> {
                    String path = inv.getArgument(0);
                    if (PUBLIC_PATH.equals(path)) {
                        return Mono.just(publicFile);
                    }
                    if (privateFile.getFilePath().equals(path)) {
                        return Mono.just(privateFile);
                    }
                    return Mono.empty();
                });
        FileStorageStrategy content = contentStrategy();
        when(strategyRegistry.getStrategy(anyString())).thenReturn(Mono.just(content));

        // 公开文件 → 正常返回
        FileDownloadVO vo = downloadPublic(PUBLIC_PATH).block();
        assertNotNull(vo);
        byte[] bytes = DataBufferUtils.join(vo.getContent())
                .map(buffer -> {
                    byte[] b = new byte[buffer.readableByteCount()];
                    buffer.read(b);
                    DataBufferUtils.release(buffer);
                    return b;
                })
                .block();
        assertArrayEquals(CONTENT, bytes);

        // 曾经的「私有文件」现在同样匿名可读
        assertNotNull(downloadPublic(privateFile.getFilePath()).block());

        // 不存在 → 404
        assertThrows(ResourceNotFoundException.class,
                () -> downloadPublic("images/2026/10/30/nope.png").block());
    }

    // ==================================================================
    // 工具
    // ==================================================================

    /**
     * 调用「按路径取公开文件」的服务方法。
     *
     * <p>写测试时这个方法还不存在，那一版用反射调用（反射找不到方法 → 测试失败，
     * 而不是编译不过）。方法实现后换回直接调用，断言未变。
     */
    private Mono<FileDownloadVO> downloadPublic(String path) {
        return downloadService.downloadPublicByPath(path);
    }

    private FileStorageStrategy contentStrategy() {
        FileStorageStrategy strategy = mock(FileStorageStrategy.class);
        when(strategy.getStrategyKey()).thenReturn("local");
        when(strategy.downloadFile(anyString()))
                .thenReturn(Mono.just(Flux.just(new DefaultDataBufferFactory().wrap(CONTENT))));
        return strategy;
    }

    private File file(Long id, String path, Long creatorId) {
        File file = new File();
        file.setId(id);
        file.setFileName(path.substring(path.lastIndexOf('/') + 1));
        file.setFilePath(path);
        file.setFileSize((long) CONTENT.length);
        file.setMimeType("image/png");
        file.setStorageType("local");
        file.setCreatorId(creatorId);
        return file;
    }
}
