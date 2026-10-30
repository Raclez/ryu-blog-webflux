package com.ryu.blog.service.impl.file;

import com.ryu.blog.dto.FileSearchDTO;
import com.ryu.blog.dto.OperatorContext;
import com.ryu.blog.entity.File;
import com.ryu.blog.repository.FileRepository;
import com.ryu.blog.repository.UserRepository;
import com.ryu.blog.strategy.FileStorageStrategy;
import com.ryu.blog.strategy.StorageStrategyRegistry;
import com.ryu.blog.vo.FileInfoVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * `/files/search` 返回的 URL 契约。
 *
 * <p><b>它守的是什么</b>：`searchFiles` 曾经自己手搓了一个 `FileInfoVO`，
 * `filePath` 直接塞存储相对路径（`images/2026/a.png`），漏掉了 `getPublicUrl` ——
 * 于是同一个字段在 `/files/search` 是裸路径、在 `/files/detail` 是完整地址，
 * 前端拿 `filePath` 当 `<img src>` 用就全是破图。
 *
 * <p>另外钉住：存储策略取不到时，那一行<b>不能</b>被静默吞掉。
 *
 * @author ryu
 */
class FileSearchUrlContractTest {

    private static final String STORAGE_PATH = "images/2026/10/30/abc.png";
    private static final String PUBLIC_URL = "https://cdn.example.com/" + STORAGE_PATH;
    private static final long FILE_ID = 7L;
    private static final long OWNER_ID = 100L;

    private FileRepository fileRepository;
    private UserRepository userRepository;
    private FileDownloadService downloadService;
    private StorageStrategyRegistry strategyRegistry;
    private FileMetadataService metadataService;
    private File file;

    @BeforeEach
    void setUp() {
        fileRepository = mock(FileRepository.class);
        userRepository = mock(UserRepository.class);
        strategyRegistry = mock(StorageStrategyRegistry.class);
        downloadService = new FileDownloadService(fileRepository, strategyRegistry);

        file = new File();
        file.setId(FILE_ID);
        file.setFileName("abc.png");
        file.setFilePath(STORAGE_PATH);
        file.setFileSize(12L);
        file.setFileType("Image");
        file.setMimeType("image/png");
        file.setStorageType("local");
        file.setCreatorId(OWNER_ID);
        file.setUploadTime(LocalDateTime.now());
        file.setUpdateTime(LocalDateTime.now());
        file.setCreateTime(LocalDateTime.now());

        when(fileRepository.searchFiles(org.mockito.ArgumentMatchers.any(), anyInt(), anyLong()))
                .thenReturn(Flux.just(file));
        when(fileRepository.countFiles(org.mockito.ArgumentMatchers.any())).thenReturn(Mono.just(1L));
        when(fileRepository.findByIdAndIsDeleted(anyLong(), anyInt())).thenReturn(Mono.just(file));
        when(userRepository.findById(anyLong())).thenReturn(Mono.empty());

        FileStorageStrategy strategy = mock(FileStorageStrategy.class);
        when(strategy.getPublicUrl(anyString())).thenReturn(Mono.just(PUBLIC_URL));
        when(strategyRegistry.getStrategy(anyString())).thenReturn(Mono.just(strategy));

        metadataService = new FileMetadataService(fileRepository, userRepository,
                strategyRegistry, downloadService);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> search() {
        FileSearchDTO dto = new FileSearchDTO();
        dto.setCurrent(1);
        dto.setSize(20);
        return metadataService.searchFiles(dto, new OperatorContext(OWNER_ID, true)).block();
    }

    @Test
    @DisplayName("filePath 必须是 getPublicUrl 的结果，不能是裸的存储路径")
    void filePathIsResolved() {
        Map<String, Object> result = search();
        assertNotNull(result);

        List<FileInfoVO> content = (List<FileInfoVO>) result.get("content");
        assertEquals(1, content.size());
        FileInfoVO vo = content.get(0);

        assertEquals(PUBLIC_URL, vo.getFilePath(),
                "filePath 必须是可直接访问的地址，不能是存储相对路径");
        assertNotEquals(STORAGE_PATH, vo.getFilePath(),
                "返回裸路径就是 bug —— 前端拿它当 <img src> 用会 404");
        assertEquals("/files/preview/" + FILE_ID, vo.getPreviewUrl());
        assertEquals("/files/download/" + FILE_ID, vo.getDownloadUrl());
    }

    @Test
    @DisplayName("存储策略取不到时，这一行仍然返回（filePath 退回原始路径），不能被吞")
    void strategyMissingDoesNotDropRow() {
        when(strategyRegistry.getStrategy(anyString())).thenReturn(Mono.empty());

        Map<String, Object> result = search();
        List<FileInfoVO> content = (List<FileInfoVO>) result.get("content");

        assertEquals(1, content.size(), "策略缺失不该让整条记录消失");
        assertEquals(STORAGE_PATH, content.get(0).getFilePath());
    }
}
