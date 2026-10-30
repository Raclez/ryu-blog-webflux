package com.ryu.blog.service.impl.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ryu.blog.controller.FileController;
import com.ryu.blog.dto.OperatorContext;
import com.ryu.blog.dto.ResourceGroupQueryDTO;
import com.ryu.blog.entity.File;
import com.ryu.blog.entity.User;
import com.ryu.blog.repository.FileRepository;
import com.ryu.blog.repository.UserRepository;
import com.ryu.blog.service.FileService;
import com.ryu.blog.strategy.FileStorageStrategy;
import com.ryu.blog.strategy.StorageStrategyRegistry;
import com.ryu.blog.vo.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 分组文件列表的 VO 泄漏护栏。
 *
 * <p><b>它守的是什么</b>：{@code GET /files/group} 早先直接返回
 * {@code PageResult<File>} —— 也就是把 {@link File} <b>实体</b>交给 Jackson 序列化。
 * 实体上的字段是「存储层关心的」，不是「列表页该展示的」：
 * {@code checksum}（可用于离线比对内容）、
 * {@code shareKey} / {@code shareExpireTime}（分享访问凭证，这两列已随表瘦身删除，
 * 但「响应里不能出现内部字段」这条断言继续留着当回归护栏）、
 * {@code cdnUrl} / {@code thumbnailPath} / {@code hasThumbnail}
 * （从无写入的死列，见整改任务 14）、
 * {@code storageType}（暴露了文件落在哪种存储上）。
 *
 * <p>实体字段增删是「加一列」的成本，而前端每次都能看见新列 ——
 * 这条边界一旦模糊，加什么字段都是「顺手」的事。
 *
 * <p><b>为什么反射断言而不是直接写死类型</b>：
 * 本测试要能在实现落地<b>之前</b>跑起来并失败（RED），
 * 所以它不引用还不存在的 VO 类型，而是沿泛型返回类型往下走，
 * 取出「叶子类型」再断言它的字段集。
 *
 * @author ryu
 */
class FileGroupVOLeakTest {

    /**
     * 不允许出现在分组列表响应里的字段。
     *
     * <p>注意 {@code storageType} 也在名单里：前端不需要知道文件落在
     * local / minio / oss 哪一种上 —— 那不是展示信息。
     */
    private static final Set<String> FORBIDDEN_FIELDS = Set.of(
            "checksum",
            "shareKey",
            "shareExpireTime",
            "cdnUrl",
            "thumbnailPath",
            "hasThumbnail",
            "storageType");

    /**
     * 分组列表<b>必须</b>提供的展示字段。
     */
    private static final Set<String> REQUIRED_FIELDS = Set.of(
            "id",
            "fileName",
            "filePath",
            "fileSize",
            "formattedSize",
            "fileType",
            "mimeType",
            "creatorId",
            "creatorName",
            "status",
            "accessType",
            "createTime");

    private static final String STORAGE_PATH = "images/2026/10/30/abc.png";
    private static final String PUBLIC_URL = "/files/public/" + STORAGE_PATH;

    private FileRepository fileRepository;
    private UserRepository userRepository;
    private StorageStrategyRegistry strategyRegistry;
    private FileAdminService adminService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // 实体与 VO 都带 LocalDateTime，不注册 JSR-310 模块的话序列化会直接抛异常，
        // 那样测试就「因为序列化失败」而红，而不是因为字段泄漏而红 —— 测的就不是同一件事了。
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        fileRepository = mock(FileRepository.class);
        userRepository = mock(UserRepository.class);

        FileStorageStrategy strategy = mock(FileStorageStrategy.class);
        when(strategy.getStrategyKey()).thenReturn("local");
        when(strategy.getPublicUrl(anyString()))
                .thenAnswer(inv -> Mono.just("/files/public/" + inv.getArgument(0)));

        strategyRegistry = mock(StorageStrategyRegistry.class);
        when(strategyRegistry.getStrategy(anyString())).thenReturn(Mono.just(strategy));

        adminService = new FileAdminService(
                fileRepository, userRepository, strategyRegistry,
                mock(FileMetadataService.class), mock(FileDownloadService.class));
    }

    // ==================================================================
    // 1. 返回类型：叶子类型必须不再是实体
    // ==================================================================

    @Test
    @DisplayName("FileService.getGroupFiles 的返回类型不再是 File 实体")
    void serviceReturnType_isNotEntity() throws Exception {
        Method method = FileService.class.getMethod(
                "getGroupFiles", ResourceGroupQueryDTO.class, OperatorContext.class);

        Class<?> leaf = leafType(method.getGenericReturnType());

        assertNotEquals(File.class, leaf,
                "getGroupFiles 仍然返回 File 实体 —— 实体上的 checksum / shareKey 等字段会被直接序列化出去");
        assertTrue(leaf.getSimpleName().endsWith("VO"),
                "分组列表应当返回一个专门的 VO，实际返回: " + leaf.getName());
    }

    @Test
    @DisplayName("FileController.getGroupFiles 的返回类型与 Service 一致，且不再是实体")
    void controllerReturnType_matchesService() throws Exception {
        Method controllerMethod = FileController.class.getMethod(
                "getGroupFiles", ResourceGroupQueryDTO.class);
        Method serviceMethod = FileService.class.getMethod(
                "getGroupFiles", ResourceGroupQueryDTO.class, OperatorContext.class);

        Class<?> controllerLeaf = leafType(controllerMethod.getGenericReturnType());
        Class<?> serviceLeaf = leafType(serviceMethod.getGenericReturnType());

        assertNotEquals(File.class, controllerLeaf,
                "Controller 仍然对外暴露 File 实体");
        assertEquals(serviceLeaf, controllerLeaf,
                "Controller 与 Service 的返回类型必须一致，否则前端拿到的 JSON 结构会与契约对不上");
    }

    // ==================================================================
    // 2. 字段集：敏感字段不许出现，展示字段必须齐全
    // ==================================================================

    @Test
    @DisplayName("分组列表 VO 不含 checksum / shareKey 等内部字段")
    void vo_hasNoSensitiveFields() {
        Class<?> leaf = leafTypeOfService();

        Set<String> declared = Arrays.stream(leaf.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)
                .collect(Collectors.toSet());

        for (String forbidden : FORBIDDEN_FIELDS) {
            assertFalse(declared.contains(forbidden),
                    "分组列表 VO 不该暴露字段 " + forbidden + "，实际字段集: " + declared);
        }
    }

    @Test
    @DisplayName("分组列表 VO 提供全部展示字段")
    void vo_hasRequiredFields() {
        Class<?> leaf = leafTypeOfService();

        Set<String> declared = Arrays.stream(leaf.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)
                .collect(Collectors.toSet());

        for (String required : REQUIRED_FIELDS) {
            assertTrue(declared.contains(required),
                    "分组列表 VO 缺少字段 " + required + "，实际字段集: " + declared);
        }
    }

    // ==================================================================
    // 3. 行为：真的调一次服务，看序列化出来的 JSON
    // ==================================================================

    @Test
    @DisplayName("分组文件列表的响应 JSON 里没有 checksum / shareKey")
    void responseJson_hasNoSensitiveKeys() throws Exception {
        stubOneFile();

        PageResult<?> page = invokeGetGroupFiles();
        assertNotNull(page);
        assertEquals(1, page.getRecords().size());

        String json = objectMapper.writeValueAsString(page.getRecords().get(0));

        assertFalse(json.contains("checksum"),
                "响应 JSON 泄露了 checksum: " + json);
        assertFalse(json.contains("shareKey"),
                "响应 JSON 泄露了 shareKey: " + json);
        assertFalse(json.contains("storageType"),
                "响应 JSON 泄露了 storageType: " + json);
    }

    @Test
    @DisplayName("分组文件列表返回公开 URL、创建者名与格式化大小")
    void responseJson_carriesPresentationData() throws Exception {
        stubOneFile();

        PageResult<?> page = invokeGetGroupFiles();
        assertNotNull(page);

        JsonNode record = objectMapper.readTree(objectMapper.writeValueAsString(page.getRecords().get(0)));

        assertEquals(PUBLIC_URL, record.path("filePath").asText(),
                "filePath 必须是可直接访问的公开 URL（前端拿它当 <img src> 用）");
        assertEquals("alice", record.path("creatorName").asText(),
                "列表项应当带上创建者名称");
        assertFalse(record.path("formattedSize").asText().isBlank(),
                "列表项应当带上格式化后的文件大小");
        assertEquals(1024L, record.path("fileSize").asLong());
        assertEquals(0, record.path("accessType").asInt());
    }

    // ==================================================================
    // 工具
    // ==================================================================

    /**
     * 取 {@code FileService.getGroupFiles} 返回类型里的叶子类型。
     */
    private Class<?> leafTypeOfService() {
        try {
            Method method = FileService.class.getMethod(
                    "getGroupFiles", ResourceGroupQueryDTO.class, OperatorContext.class);
            return leafType(method.getGenericReturnType());
        } catch (NoSuchMethodException e) {
            throw new AssertionError("FileService 上找不到 getGroupFiles", e);
        }
    }

    /**
     * 沿着泛型参数一路往下走，取最里层的类型。
     *
     * <p>{@code Mono<PageResult<File>>} → {@code File}；
     * {@code Mono<Result<PageResult<VO>>>} → {@code VO}。
     */
    private static Class<?> leafType(Type type) {
        Type current = type;
        while (current instanceof ParameterizedType parameterized) {
            Type[] args = parameterized.getActualTypeArguments();
            if (args.length == 0) {
                break;
            }
            current = args[0];
        }
        return (Class<?>) current;
    }

    /**
     * 调用 {@code FileAdminService.getGroupFiles} 并拿到分页结果。
     *
     * <p>用原始类型接收 —— 这样无论返回的是 {@code PageResult<File>}
     * 还是 {@code PageResult<FileGroupItemVO>}，本测试都编译得过。
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private PageResult<?> invokeGetGroupFiles() {
        ResourceGroupQueryDTO dto = new ResourceGroupQueryDTO();
        dto.setGroupId(7L);
        dto.setCurrentPage(1L);
        dto.setPageSize(20L);

        Mono raw = adminService.getGroupFiles(dto, new OperatorContext(100L, true));
        return (PageResult<?>) raw.block();
    }

    /**
     * 造一个分组下有且只有一个公开文件的场景。
     */
    private void stubOneFile() {
        File file = new File();
        file.setId(1L);
        file.setFileName("abc.png");
        file.setFilePath(STORAGE_PATH);
        file.setFileSize(1024L);
        file.setFileType("image");
        file.setMimeType("image/png");
        file.setStorageType("local");
        file.setCreatorId(100L);
        file.setAccessType(FileAccessPolicy.ACCESS_PUBLIC);
        file.setStatus(1);
        file.setChecksum("d41d8cd98f00b204e9800998ecf8427e");
        file.setCreateTime(LocalDateTime.of(2026, 10, 30, 12, 0, 0));

        when(fileRepository.findByGroupId(anyLong(), anyInt(), anyLong()))
                .thenReturn(Flux.just(file));
        when(fileRepository.countByGroupId(anyLong())).thenReturn(Mono.just(1L));

        User user = new User();
        user.setId(100L);
        user.setUsername("alice");
        when(userRepository.findById(anyLong())).thenReturn(Mono.just(user));
    }
}
