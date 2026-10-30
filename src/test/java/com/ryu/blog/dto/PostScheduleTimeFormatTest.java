package com.ryu.blog.dto;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ryu.blog.enums.PublishMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 定时发布时间的**请求体格式**护栏。
 *
 * <p>前端的日期选择器 {@code value-format} 是 {@code YYYY-MM-DD HH:mm:ss}，
 * 而 DTO 上写着 {@code @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")}。
 * 这两处必须一致 —— 一旦有一边改了，另一边会在**运行时**炸成 400，
 * 而且报错信息是 Jackson 的日期解析异常，前端只会看到「请求失败」。
 *
 * <p>特别要注意：ISO 串（{@code 2026-10-30T08:00:00}）在这里是**不合法**的 ——
 * 带 {@code @JsonFormat} 的字段按 pattern 严格解析，容不下 ISO 的 {@code T} 分隔符。
 *
 * @author ryu
 */
class PostScheduleTimeFormatTest {

    /**
     * 与应用主 ObjectMapper 保持一致的最小构造。
     *
     * <p>{@code FAIL_ON_UNKNOWN_PROPERTIES} 的关闭是 Spring Boot 的默认行为
     * （{@code JacksonAutoConfiguration} 里关的），这里显式关掉才是「模拟真实运行时」。
     */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Test
    @DisplayName("scheduleTime 用 'yyyy-MM-dd HH:mm:ss' 能正常解析（前端选择器的格式）")
    void parsesSpaceSeparatedDateTime() throws Exception {
        String json = """
                {"title":"定时文章","publishMode":"SCHEDULED","scheduleTime":"2026-10-30 08:00:00"}
                """;

        PostCreateDTO dto = objectMapper.readValue(json, PostCreateDTO.class);

        assertEquals(LocalDateTime.of(2026, 10, 30, 8, 0, 0), dto.getScheduleTime());
        assertEquals(PublishMode.SCHEDULED, dto.getPublishMode());
    }

    @Test
    @DisplayName("scheduleTime 用 ISO 串会被拒 —— 别让文档示例把人带沟里")
    void rejectsIsoDateTime() {
        String json = """
                {"title":"定时文章","publishMode":"SCHEDULED","scheduleTime":"2026-10-30T08:00:00"}
                """;

        assertThrows(Exception.class, () -> objectMapper.readValue(json, PostCreateDTO.class),
                "ISO 串应当解析失败（DTO 上有 @JsonFormat(pattern=\"yyyy-MM-dd HH:mm:ss\")）");
    }

    @Test
    @DisplayName("publishMode 缺省 = null（= 保持现状），不能被默认值悄悄改掉")
    void publishModeAbsentStaysNull() throws Exception {
        PostCreateDTO dto = objectMapper.readValue("{\"title\":\"只改标题\"}", PostCreateDTO.class);

        assertNull(dto.getPublishMode(),
                "publishMode 必须保持 null —— 服务端靠它区分「保持现状」与「改发布状态」");
    }

    @Test
    @DisplayName("PostUpdateDTO 的 scheduleTime 格式与创建一致")
    void updateDtoUsesSameFormat() throws Exception {
        String json = """
                {"id":1,"title":"改期","publishMode":"SCHEDULED","scheduleTime":"2026-10-30 08:00:00"}
                """;

        PostUpdateDTO dto = objectMapper.readValue(json, PostUpdateDTO.class);

        assertEquals(LocalDateTime.of(2026, 10, 30, 8, 0, 0), dto.getScheduleTime());
    }

    @Test
    @DisplayName("未知字段被忽略 —— 老前端继续在请求体里带 status 也不会 400")
    void unknownFieldsAreIgnored() throws Exception {
        PostUpdateDTO dto = objectMapper.readValue(
                "{\"id\":1,\"title\":\"老前端\",\"status\":2}", PostUpdateDTO.class);

        assertEquals(1L, dto.getId().longValue());
        assertNull(dto.getPublishMode(),
                "status 不该被映射成发布方式（状态变更走 PUT /posts/status）");
    }
}
