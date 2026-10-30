package com.ryu.blog.service.impl.file;

import com.ryu.blog.dto.FileSearchDTO;
import com.ryu.blog.dto.OperatorContext;
import com.ryu.blog.dto.UploadOptionsDTO;
import com.ryu.blog.entity.File;
import com.ryu.blog.exception.PermissionDeniedException;
import com.ryu.blog.exception.ResourceNotFoundException;
import com.ryu.blog.repository.FileRepository;
import com.ryu.blog.repository.UserRepository;
import com.ryu.blog.strategy.BatchDeleteCapable;
import com.ryu.blog.strategy.ChecksumCapable;
import com.ryu.blog.strategy.FileStorageStrategy;
import com.ryu.blog.strategy.MetadataCapable;
import com.ryu.blog.strategy.MultipartCapable;
import com.ryu.blog.strategy.PresignCapable;
import com.ryu.blog.strategy.StorageConfigManager;
import com.ryu.blog.strategy.StorageStrategyRegistry;
import com.ryu.blog.strategy.ThumbnailCapable;
import com.ryu.blog.strategy.impl.LocalStorageStrategy;
import com.ryu.blog.vo.FileUploadVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * 文件模块全链路验收测试 —— 对应 Stage 6 的验收标准：
 * <ul>
 *   <li>上传 → 查询 → 下载 → 删除 全链路；</li>
 *   <li>跨用户访问返回 403 / 404；</li>
 *   <li>存储策略切换后<b>旧文件仍可读</b>、新文件落到新策略。</li>
 * </ul>
 *
 * <p><b>为什么是服务层而不是 HTTP 层</b>：HTTP 层要连真库、还要在测试库里留下
 * 文件记录（写共享的开发库是运维动作，不该由测试悄悄做）。
 * 这里把仓储换成内存实现、把存储换成临时目录，
 * 整条链路（策略选型 → 落盘 → 落库 → 归属判定 → 删除）都是真的跑的，
 * 只有「持久化」这一层是替身。
 *
 * <p><b>被测的安全边界</b>：整改前文件模块整条链路没有归属校验，
 * 任何登录用户都能下载 / 改 / 删别人的文件。
 *
 * @author ryu
 */
class FileLifecycleAcceptanceTest {

    /** 上传者 */
    private static final Long OWNER_ID = 100L;
    /** 另一个登录用户 */
    private static final Long STRANGER_ID = 200L;

    private static final OperatorContext OWNER = new OperatorContext(OWNER_ID, false);
    private static final OperatorContext STRANGER = new OperatorContext(STRANGER_ID, false);

    private static final byte[] CONTENT = "ryu-blog acceptance".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path storageRoot;

    /** 内存版仓储 */
    private final Map<Long, File> store = new LinkedHashMap<>();
    private final AtomicLong idSeq = new AtomicLong(1);
    /** 当前活跃的存储策略 key —— 模拟「管理员切换了存储策略」 */
    private final AtomicReference<String> activeStrategyKey = new AtomicReference<>("local");

    private FileRepository fileRepository;
    private FileUploadService uploadService;
    private FileDownloadService downloadService;
    private FileMetadataService metadataService;
    private FileAdminService adminService;
    private InMemoryStorageStrategy memoryStrategy;

    @BeforeEach
    void setUp() {
        fileRepository = mock(FileRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findById(anyLong())).thenReturn(Mono.empty());

        StorageConfigManager configManager = mock(StorageConfigManager.class);
        when(configManager.getActiveStrategyKey()).thenAnswer(inv -> activeStrategyKey.get());
        when(configManager.getStrategyConfig(anyString())).thenReturn(Mono.empty());
        when(configManager.getAccessUrlAsync(anyString())).thenReturn(Mono.just(""));
        when(configManager.getConfigPropertyAsync(anyString(), anyString(), any()))
                .thenAnswer(inv -> {
                    String key = inv.getArgument(1);
                    String defaultValue = inv.getArgument(2);
                    if ("basePath".equals(key)) {
                        return Mono.just(storageRoot.toString());
                    }
                    return Mono.just(defaultValue == null ? "" : defaultValue);
                });

        // 真实落盘的本地策略 + 内存策略（用于验证策略切换）
        LocalStorageStrategy localStrategy = new LocalStorageStrategy(configManager);
        ReflectionTestUtils.setField(localStrategy, "defaultBasePath", storageRoot.toString());
        memoryStrategy = new InMemoryStorageStrategy();

        StorageStrategyRegistry registry = new StorageStrategyRegistry(
                List.of(localStrategy, memoryStrategy), configManager);
        registry.init();

        stubRepository();

        downloadService = new FileDownloadService(fileRepository, registry);
        metadataService = new FileMetadataService(fileRepository, userRepository, registry, downloadService);
        uploadService = new FileUploadService(fileRepository, mock(ReactiveRedisTemplate.class),
                registry, configManager);
        // @Value 注入的字段在手工 new 的实例里是 null，这里补上
        ReflectionTestUtils.setField(uploadService, "maxSizeStr", "10MB");
        ReflectionTestUtils.setField(uploadService, "allowedTypesStr", "png,jpg,pdf,txt");
        adminService = new FileAdminService(fileRepository, userRepository, registry,
                metadataService, downloadService);
    }

    /**
     * 用内存 Map 替掉数据库
     */
    private void stubRepository() {
        when(fileRepository.save(any(File.class))).thenAnswer(inv -> {
            File file = inv.getArgument(0);
            if (file.getId() == null) {
                file.setId(idSeq.getAndIncrement());
            }
            store.put(file.getId(), file);
            return Mono.just(file);
        });
        when(fileRepository.findByIdAndIsDeleted(anyLong(), anyInt())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            Integer isDeleted = inv.getArgument(1);
            File file = store.get(id);
            boolean deleted = file != null && Boolean.TRUE.equals(file.getIsDeleted());
            if (file == null || deleted != (isDeleted != null && isDeleted == 1)) {
                return Mono.empty();
            }
            return Mono.just(file);
        });
        when(fileRepository.findFirstByChecksumAndCreatorIdAndIsDeleted(anyString(), anyLong(), anyInt()))
                .thenAnswer(inv -> {
                    String checksum = inv.getArgument(0);
                    Long creatorId = inv.getArgument(1);
                    return store.values().stream()
                            .filter(f -> !Boolean.TRUE.equals(f.getIsDeleted()))
                            .filter(f -> creatorId.equals(f.getCreatorId()))
                            .filter(f -> checksum.equals(f.getChecksum()))
                            .findFirst()
                            .map(Mono::just)
                            .orElseGet(Mono::empty);
                });
        when(fileRepository.findByFilePathAndIsDeleted(anyString(), anyInt())).thenAnswer(inv -> {
            String path = inv.getArgument(0);
            Integer isDeleted = inv.getArgument(1);
            return store.values().stream()
                    .filter(f -> path.equals(f.getFilePath()))
                    .filter(f -> (Boolean.TRUE.equals(f.getIsDeleted()) ? 1 : 0)
                            == (isDeleted == null ? 0 : isDeleted))
                    .findFirst()
                    .map(Mono::just)
                    .orElseGet(Mono::empty);
        });
        when(fileRepository.searchFiles(any(FileSearchDTO.class), anyInt(), anyLong())).thenAnswer(inv -> {
            FileSearchDTO query = inv.getArgument(0);
            int limit = inv.getArgument(1);
            long offset = inv.getArgument(2);
            List<File> matched = store.values().stream()
                    .filter(f -> !Boolean.TRUE.equals(f.getIsDeleted()))
                    .filter(f -> query.getCreatorId() == null || query.getCreatorId().equals(f.getCreatorId()))
                    .toList();
            return Flux.fromIterable(matched).skip(offset).take(limit);
        });
        when(fileRepository.countFiles(any(FileSearchDTO.class))).thenAnswer(inv -> {
            FileSearchDTO query = inv.getArgument(0);
            long count = store.values().stream()
                    .filter(f -> !Boolean.TRUE.equals(f.getIsDeleted()))
                    .filter(f -> query.getCreatorId() == null || query.getCreatorId().equals(f.getCreatorId()))
                    .count();
            return Mono.just(count);
        });
    }

    // ==================================================================
    // 验收 1：上传 → 查询 → 下载 → 删除
    // ==================================================================

    @Test
    @DisplayName("全链路：上传 → 查询 → 下载 → 删除")
    void uploadQueryDownloadDelete() {
        // 上传
        FileUploadVO uploaded = uploadService
                .handleFileUpload(filePart("acceptance.txt", CONTENT), new UploadOptionsDTO(), OWNER_ID)
                .block();
        assertNotNull(uploaded);
        assertNotNull(uploaded.getFileId());

        // 落库记录归属上传者（而不是历史上写死的 1L）
        File record = store.get(uploaded.getFileId());
        assertNotNull(record);
        assertEquals(OWNER_ID, record.getCreatorId());
        assertEquals("local", record.getStorageType());

        // 查询：上传者能看到自己刚传的文件
        FileSearchDTO query = new FileSearchDTO();
        query.setCreatorId(OWNER_ID);
        Map<String, Object> searchResult = metadataService.searchFiles(query, OWNER).block();
        assertEquals(1L, ((Number) searchResult.get("total")).longValue(),
                "上传者应当能搜到自己刚上传的文件");

        // 下载：内容一致
        byte[] downloaded = downloadService.downloadByPath(record.getFilePath()).block();
        assertNotNull(downloaded);
        assertArrayEquals(CONTENT, downloaded);

        // 删除
        adminService.deleteFile(uploaded.getFileId(), OWNER).block();

        // 删除后不可见 → 404
        assertThrows(ResourceNotFoundException.class,
                () -> downloadService.downloadById(uploaded.getFileId(), OWNER).block());
    }

    // ==================================================================
    // 验收 2：跨用户访问
    // ==================================================================

    @Test
    @DisplayName("跨用户访问：读放开（文件一律公开），写仍然只有主人/管理员")
    void strangerCanReadButCannotWrite() {
        FileUploadVO uploaded = uploadService
                .handleFileUpload(filePart("private.txt", CONTENT), new UploadOptionsDTO(), OWNER_ID)
                .block();
        Long fileId = uploaded.getFileId();

        // 读 → 放行：文件可见性已下线（对象存储桶本身匿名可读，
        // 那个字段挡不住直链，只制造「私有文件是安全的」这种错觉）
        assertNotNull(downloadService.downloadById(fileId, STRANGER).block());
        assertNotNull(metadataService.getFileInfo(fileId, STRANGER).block());

        // 删除 → 403
        assertThrows(PermissionDeniedException.class,
                () -> adminService.deleteFile(fileId, STRANGER).block());

        // 搜索 → 看不到（自己的文件列表里没有别人的文件）
        FileSearchDTO query = new FileSearchDTO();
        Map<String, Object> result = metadataService.searchFiles(query, STRANGER).block();
        assertEquals(0L, ((Number) result.get("total")).longValue(),
                "非管理员的搜索结果必须收敛到自己的文件");

        // 文件还在（越权删除没有生效）
        assertTrue(Boolean.FALSE.equals(store.get(fileId).getIsDeleted()));
    }

    // ==================================================================
    // 验收 3：存储策略切换
    // ==================================================================

    @Test
    @DisplayName("策略切换后：旧文件仍可读，新文件落到新策略")
    void oldFilesRemainReadableAfterStrategySwitch() {
        // 切策略之前上传
        FileUploadVO before = uploadService
                .handleFileUpload(filePart("before-switch.txt", CONTENT), new UploadOptionsDTO(), OWNER_ID)
                .block();
        File oldRecord = store.get(before.getFileId());
        assertEquals("local", oldRecord.getStorageType());

        // 管理员把活跃策略切到内存策略
        activeStrategyKey.set("memory");

        // 旧文件仍然可读 —— 读的是记录里的 storage_type，不是「当前活跃策略」
        byte[] downloaded = downloadService.downloadByPath(oldRecord.getFilePath()).block();
        assertNotNull(downloaded);
        assertArrayEquals(CONTENT, downloaded);

        // 新文件落到新策略（内容要不同 —— 同内容同校验和会被去重策略挡下）
        byte[] newContent = "ryu-blog after switch".getBytes(StandardCharsets.UTF_8);
        FileUploadVO after = uploadService
                .handleFileUpload(filePart("after-switch.txt", newContent), new UploadOptionsDTO(), OWNER_ID)
                .block();
        File newRecord = store.get(after.getFileId());
        assertEquals("memory", newRecord.getStorageType(),
                "切换后的新文件必须落到新策略上");

        // 两个文件都能下载，且内容一致
        assertArrayEquals(CONTENT, downloadService.downloadByPath(oldRecord.getFilePath()).block());
        assertArrayEquals(newContent, downloadService.downloadByPath(newRecord.getFilePath()).block());
    }

    // ==================================================================
    // 工具
    // ==================================================================

    private FilePart filePart(String fileName, byte[] content) {
        FilePart part = mock(FilePart.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentLength(content.length);
        when(part.filename()).thenReturn(fileName);
        when(part.headers()).thenReturn(headers);
        when(part.content()).thenAnswer(inv -> Flux.just(
                new DefaultDataBufferFactory().wrap(content)));
        return part;
    }

    /**
     * 纯内存的存储策略 —— 用来验证「策略切换」这条路径
     */
    private static final class InMemoryStorageStrategy implements FileStorageStrategy,
            MetadataCapable, ChecksumCapable, PresignCapable, ThumbnailCapable,
            BatchDeleteCapable, MultipartCapable {

        private final Map<String, byte[]> objects = new LinkedHashMap<>();

        @Override
        public String getStrategyKey() {
            return "memory";
        }

        public Mono<String> uploadFile(FilePart filePart) {
            return Mono.error(new UnsupportedOperationException());
        }

        @Override
        public Mono<String> uploadFile(Flux<DataBuffer> dataBufferFlux, String fileName, long size) {
            return DataBufferUtils.join(dataBufferFlux)
                    .map(buffer -> {
                        byte[] bytes = new byte[buffer.readableByteCount()];
                        buffer.read(bytes);
                        DataBufferUtils.release(buffer);
                        String path = "memory/" + fileName + "-" + objects.size();
                        objects.put(path, bytes);
                        return path;
                    });
        }

        @Override
        public Mono<Flux<DataBuffer>> downloadFile(String path) {
            byte[] bytes = objects.get(path);
            if (bytes == null) {
                return Mono.just(Flux.empty());
            }
            return Mono.just(Flux.just(new DefaultDataBufferFactory().wrap(bytes)));
        }

        @Override
        public Mono<Boolean> deleteFile(String path) {
            return Mono.just(objects.remove(path) != null);
        }

        @Override
        public Mono<Map<String, Boolean>> batchDeleteFiles(List<String> paths) {
            Map<String, Boolean> result = new LinkedHashMap<>();
            paths.forEach(path -> result.put(path, objects.remove(path) != null));
            return Mono.just(result);
        }

        @Override
        public Mono<Boolean> fileExists(String path) {
            return Mono.just(objects.containsKey(path));
        }

        @Override
        public Mono<Map<String, Object>> getMetadata(String path) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            byte[] bytes = objects.get(path);
            if (bytes != null) {
                metadata.put("size", bytes.length);
            }
            return Mono.just(metadata);
        }

        @Override
        public Mono<String> generatePreviewUrl(String path, long expireSeconds) {
            return Mono.just("/memory/" + path);
        }

        @Override
        public Mono<String> generateDownloadUrl(String path, long expireSeconds) {
            return Mono.just("/memory/" + path + "?download=true");
        }

        @Override
        public Mono<String> calculateFileChecksum(String path) {
            return Mono.just("");
        }

        @Override
        public Mono<String> generateThumbnail(String filePath, String thumbnailPath, int width, int height) {
            return Mono.error(new UnsupportedOperationException());
        }

        @Override
        public Mono<Map<String, String>> initiateMultipartUpload(String fileName, long fileSize,
                                                                 Map<String, String> storageParams) {
            return Mono.just(new LinkedHashMap<>());
        }

        @Override
        public Mono<String> uploadPart(String uploadId, int partNumber, Flux<DataBuffer> content) {
            return Mono.error(new UnsupportedOperationException());
        }

        @Override
        public Mono<String> completeMultipartUpload(String uploadId, List<String> partETags) {
            return Mono.error(new UnsupportedOperationException());
        }

        @Override
        public Mono<Boolean> abortMultipartUpload(String uploadId) {
            return Mono.just(true);
        }

        @Override
        public Mono<String> getPublicUrl(String path) {
            return Mono.just("/memory/" + path);
        }
    }
}
