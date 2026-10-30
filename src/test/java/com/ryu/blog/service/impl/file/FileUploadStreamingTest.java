package com.ryu.blog.service.impl.file;

import com.ryu.blog.constant.ErrorCodeConstants;
import com.ryu.blog.dto.UploadOptionsDTO;
import com.ryu.blog.entity.File;
import com.ryu.blog.exception.BusinessException;
import com.ryu.blog.repository.FileRepository;
import com.ryu.blog.strategy.FileStorageStrategy;
import com.ryu.blog.strategy.StorageConfigManager;
import com.ryu.blog.strategy.StorageStrategyRegistry;
import com.ryu.blog.vo.FileUploadVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 上传路径的流式护栏。
 *
 * <p><b>它守的是什么</b>：上传早先是
 * {@code DataBufferUtils.join(整个文件) → byte[] → 再包成 DataBuffer → Flux.just(一个)}
 * —— 整个文件先进堆里，再复制一遍，再包一层。10MB 的文件在堆上要占 20~30MB，
 * 而批量上传时 {@code flatMap} **没有并发上限**，N 个文件同时在堆里。
 *
 * <p>更糟的是：{@code FileStorageStrategy.uploadFile(Flux<DataBuffer>, ...)}
 * 这个接口本来就是流式的，{@code LocalStorageStrategy} 用
 * {@code DataBufferUtils.write(flux, channel, 0)} 直接写文件通道 ——
 * 是<b>调用方</b>把策略的流式能力废掉了。
 *
 * <p>本测试盯三件事：
 * <ol>
 *   <li>策略收到的是<b>多个</b> DataBuffer（没有被 join 成一块）；</li>
 *   <li>超限时抛 {@code fileSizeLimit} 并且<b>源流被取消</b>（不是把整个文件读完）；</li>
 *   <li>批量上传时策略层同时在途的文件数有上限。</li>
 * </ol>
 *
 * @author ryu
 */
class FileUploadStreamingTest {

    /**
     * 源流每个分片的大小 —— 刻意做成远小于「文件大小」，
     * 这样「被 join 成一块」和「原样流下去」在计数上一眼可辨。
     */
    private static final int CHUNK = 8 * 1024;

    private FileRepository fileRepository;
    private StorageStrategyRegistry strategyRegistry;
    private FileStorageStrategy strategy;
    private FileUploadService service;

    @BeforeEach
    void setUp() {
        fileRepository = mock(FileRepository.class);
        when(fileRepository.save(any(File.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(fileRepository.findFirstByChecksumAndCreatorIdAndIsDeleted(anyString(), anyLong(), anyInt()))
                .thenReturn(Mono.empty());

        strategy = mock(FileStorageStrategy.class);
        when(strategy.getStrategyKey()).thenReturn("local");
        when(strategy.getPublicUrl(anyString()))
                .thenAnswer(inv -> Mono.just("/files/public/" + inv.getArgument(0)));
        // 默认让策略把流传下去并把它读空 —— 上限校验是「在流上」发生的，
        // 只有真正订阅了这条流才会触发。各个用例可以再覆盖这个桩。
        when(strategy.uploadFile(any(), anyString(), anyLong())).thenAnswer(inv -> {
            Flux<DataBuffer> content = inv.getArgument(0);
            return content.then(Mono.just("images/2026/10/30/file.png"));
        });

        strategyRegistry = mock(StorageStrategyRegistry.class);
        when(strategyRegistry.getActiveStrategy()).thenReturn(Mono.just(strategy));
        when(strategyRegistry.getStrategy(anyString())).thenReturn(Mono.just(strategy));

        @SuppressWarnings("unchecked")
        ReactiveRedisTemplate<String, Object> redis = mock(ReactiveRedisTemplate.class);
        StorageConfigManager configManager = mock(StorageConfigManager.class);

        service = new FileUploadService(fileRepository, redis, strategyRegistry, configManager);
    }

    // ==================================================================
    // 1. 流式：策略收到多个 DataBuffer
    // ==================================================================

    @Test
    @DisplayName("上传 2MB：策略收到的是多个 DataBuffer，而不是 join 出来的一整块")
    void upload_streamsInsteadOfJoining() {
        AtomicInteger bufferCount = new AtomicInteger();
        AtomicLong receivedBytes = new AtomicLong();

        // 用 doAnswer(...).when(...) 而不是 when(...).thenAnswer(...)：
        // setUp 里已经给同一个方法打过桩，而 Mockito 在「重打桩」时会先执行一次
        // 已注册的答案（参数是 matcher 占位符，全是 null）—— 直通桩会在那里 NPE。
        doAnswer(inv -> {
            Flux<DataBuffer> content = inv.getArgument(0);
            return content
                    .doOnNext(buffer -> {
                        bufferCount.incrementAndGet();
                        receivedBytes.addAndGet(buffer.readableByteCount());
                    })
                    .then(Mono.just("images/2026/10/30/photo.png"));
        }).when(strategy).uploadFile(any(), anyString(), anyLong());

        FileUploadVO vo = service
                .handleFileUpload(partOf("photo.png", 2 * 1024 * 1024), new UploadOptionsDTO(), 1L)
                .block();

        assertNotNull(vo);
        assertTrue(bufferCount.get() > 1,
                "策略只收到了 " + bufferCount.get()
                        + " 个 DataBuffer —— 上传路径把整个文件 join 成了一块内存，"
                        + "策略接口的流式能力被调用方废掉了");
        assertEquals(2 * 1024 * 1024L, receivedBytes.get(),
                "策略收到的字节数必须与文件大小一致");
    }

    // ==================================================================
    // 2. 大小上限：超限即拒，且源流被取消
    // ==================================================================

    @Test
    @DisplayName("超过 max-size 的流：抛 fileSizeLimit，且源流被取消")
    void oversizeStream_isRejectedAndSourceCancelled() {
        ReflectionTestUtils.setField(service, "maxSizeStr", "1KB");

        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicLong emitted = new AtomicLong();

        Flux<DataBuffer> content = Flux.range(0, 400)
                .map(i -> buffer(CHUNK))
                .doOnNext(b -> emitted.addAndGet(b.readableByteCount()))
                .doOnCancel(() -> cancelled.set(true));

        FilePart part = mock(FilePart.class);
        when(part.filename()).thenReturn("big.png");
        // 故意不给 Content-Length：客户端可以声明一个小的，也可以不给
        when(part.headers()).thenReturn(new HttpHeaders());
        when(part.content()).thenReturn(content);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.handleFileUpload(part, new UploadOptionsDTO(), 1L).block());

        assertEquals(ErrorCodeConstants.FILE_SIZE_LIMIT, ex.getCode(),
                "超限必须报「文件大小超出限制」");
        assertTrue(cancelled.get(),
                "超限时源流必须被取消 —— 不能把整个文件读完再拒绝");
        assertTrue(emitted.get() <= 1024L + CHUNK,
                "超限前只应从源流取到「上限 + 一个分片」的数据，实际取了 " + emitted.get() + " 字节");
    }

    // ==================================================================
    // 3. 批量上传：并发有上限
    // ==================================================================

    @Test
    @DisplayName("批量上传 10 个文件：策略层同时在途的文件数不超过 4")
    void batchUpload_limitsConcurrency() {
        ReflectionTestUtils.setField(service, "maxSizeStr", "10MB");

        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();

        doAnswer(inv -> {
            int current = inFlight.incrementAndGet();
            maxInFlight.accumulateAndGet(current, Math::max);

            Flux<DataBuffer> content = inv.getArgument(0);
            return content
                    .then(Mono.delay(Duration.ofMillis(60)))
                    .map(tick -> {
                        inFlight.decrementAndGet();
                        return "images/2026/10/30/file.png";
                    });
        }).when(strategy).uploadFile(any(), anyString(), anyLong());

        Flux<FilePart> parts = Flux.range(0, 10)
                .map(i -> partOf("file" + i + ".png", 64 * 1024));

        List<FileUploadVO> results = service
                .handleBatchFileUpload(parts, new UploadOptionsDTO(), 1L)
                .collectList()
                .block();

        assertNotNull(results);
        assertEquals(10, results.size(), "10 个文件都应当上传成功");
        assertTrue(maxInFlight.get() <= 4,
                "同时在途的文件数达到了 " + maxInFlight.get()
                        + " —— 批量上传没有并发上限，N 个文件会同时占住堆");
    }

    // ==================================================================
    // 工具
    // ==================================================================

    /**
     * 造一个分片上传用的 {@link FilePart}（不声明 Content-Length）。
     */
    private FilePart partOf(String fileName, int totalBytes) {
        FilePart part = mock(FilePart.class);
        when(part.filename()).thenReturn(fileName);
        when(part.headers()).thenReturn(new HttpHeaders());
        when(part.content()).thenReturn(contentOf(totalBytes));
        return part;
    }

    /**
     * 把 {@code totalBytes} 拆成若干个 {@link #CHUNK} 大小的分片。
     */
    private Flux<DataBuffer> contentOf(int totalBytes) {
        int fullChunks = totalBytes / CHUNK;
        int rest = totalBytes % CHUNK;

        Flux<DataBuffer> flux = Flux.range(0, fullChunks).map(i -> buffer(CHUNK));
        if (rest > 0) {
            flux = flux.concatWith(Flux.just(buffer(rest)));
        }
        return flux;
    }

    /**
     * 造一个指定大小的 {@link DataBuffer}。
     */
    private DataBuffer buffer(int size) {
        byte[] data = new byte[size];
        Arrays.fill(data, (byte) 'A');
        return new DefaultDataBufferFactory().wrap(data);
    }
}
