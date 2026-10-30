package com.ryu.blog.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ryu.blog.config.ConditionalOnAiEnabled;
import com.ryu.blog.dto.AiAssistAction;
import com.ryu.blog.dto.AiAssistRequest;
import com.ryu.blog.dto.AiAssistResult;
import com.ryu.blog.dto.AiGenerationRequest;
import com.ryu.blog.dto.AiGenerationResult;
import com.ryu.blog.entity.AiGenerationHistory;
import com.ryu.blog.exception.BusinessException;
import com.ryu.blog.repository.AiGenerationHistoryRepository;
import com.ryu.blog.service.AiAssistService;
import com.ryu.blog.service.AiUsageStatisticsService;
import com.ryu.blog.service.ContentSafetyService;
import com.ryu.blog.service.RateLimitService;
import com.ryu.blog.service.ai.AiInvocationGuard;
import com.ryu.blog.service.ai.AiProviderFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AI 辅助编辑服务实现
 *
 * <p>设计取向：<b>动作粒度小</b>。整篇生成一次动辄几千 token、几十秒，用户几乎不会用；
 * 而「把这段话润色一下」「给这篇起 5 个标题」这类小动作秒回、token 可控，才是能嵌进
 * 编辑器日常流程的东西。
 *
 * <p>所有调用都复用现有的 Provider 抽象、配额限流、内容安全与用量统计，
 * 不引入新的架构概念。
 *
 * @author ryu
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnAiEnabled
public class AiAssistServiceImpl implements AiAssistService {

    private final AiProviderFactory providerFactory;
    private final RateLimitService rateLimitService;
    private final AiUsageStatisticsService statisticsService;
    private final ContentSafetyService contentSafetyService;
    private final AiGenerationHistoryRepository historyRepository;
    private final AiInvocationGuard invocationGuard;
    private final ObjectMapper objectMapper;

    /** 动作定义：code -> 规格 */
    private static final Map<String, ActionSpec> ACTIONS = new LinkedHashMap<>();

    static {
        register(new ActionSpec("polish", "润色",
                "优化表达、衔接与结构，保持原意与技术细节不变", true, """
                        你是一名中文技术写作编辑。请对下面的内容进行润色。

                        要求：
                        1. 保持原意、事实和技术细节不变，不要新增观点或结论
                        2. 优化语句表达、段落衔接与结构层次
                        3. 保留原有的 Markdown 格式（标题、列表、代码块）
                        4. 直接输出润色后的完整内容，不要输出解释，不要用代码块包裹全文

                        待润色内容：
                        {content}"""));

        register(new ActionSpec("continue", "续写",
                "基于已有内容自然续写，保持语气与术语一致", true, """
                        你是一名中文技术写作编辑。请基于下面的内容自然续写。

                        要求：
                        1. 与上文的语气、术语、叙述节奏保持一致
                        2. 只输出续写的部分，不要重复已有内容，也不要复述上文
                        3. 续写约 {length} 字
                        4. 直接输出续写内容，不要加标题

                        已有内容：
                        {content}"""));

        register(new ActionSpec("titles", "标题候选",
                "为文章生成多个候选标题，每行一个", false, """
                        你是一名中文技术博客的标题编辑。请为下面的文章内容生成 {count} 个候选标题。

                        要求：
                        1. 每个标题单独一行，不要编号、不要解释、不要加引号
                        2. 每个标题控制在 30 个汉字以内，准确概括文章主题
                        3. 风格各异，覆盖不同角度（例如问题导向、收益导向、关键词导向）
                        4. 只输出标题本身

                        文章内容：
                        {content}"""));

        register(new ActionSpec("summary", "生成摘要",
                "生成 100-200 字的文章摘要", true, """
                        你是一名中文技术博客的编辑。请为下面的文章写一段摘要。

                        要求：
                        1. 长度 100-200 字
                        2. 概括文章解决的问题、涉及的技术与结论
                        3. 直接输出摘要正文，不要加标题或前缀

                        文章内容：
                        {content}"""));

        register(new ActionSpec("seo", "SEO 元信息",
                "生成 SEO 标题与描述", false, """
                        你是一名 SEO 编辑。请为下面的文章生成 SEO 元信息。

                        要求：
                        1. title：不超过 30 个汉字，包含主要关键词
                        2. description：不超过 80 个汉字，概括文章价值并自然包含关键词
                        3. 严格输出如下 JSON，不要有任何额外文字，也不要用代码块包裹：
                        {"title":"...","description":"..."}

                        文章内容：
                        {content}"""));

        register(new ActionSpec("translate", "翻译",
                "翻译成指定语言，保留 Markdown 结构", true, """
                        你是一名技术翻译。请把下面的内容翻译成{target}。

                        要求：
                        1. 保留原有的 Markdown 结构（标题、列表、代码块）
                        2. 代码块内的代码保持原样，只翻译注释与说明文字
                        3. 术语准确，保持专业性
                        4. 只输出译文

                        原文：
                        {content}"""));

        register(new ActionSpec("explain", "解读",
                "解释内容的作用与关键机制", true, """
                        你是一名技术讲解者。请解释下面的内容。

                        要求：
                        1. 先用一句话概括它的作用
                        2. 再分点展开关键机制，必要时给出简短示例
                        3. 面向有经验的开发者，不做基础科普
                        4. 直接输出解释内容

                        待解释内容：
                        {content}"""));
    }

    private static void register(ActionSpec spec) {
        ACTIONS.put(spec.code(), spec);
    }

    @Override
    public Mono<AiAssistResult> assist(AiAssistRequest request) {
        return Mono.defer(() -> {
            ActionSpec spec = resolveAction(request.getAction());
            String prompt = buildPrompt(spec, request);
            long startTime = System.currentTimeMillis();

            return rateLimitService.checkAndIncrement(request.getUserId())
                    .then(providerFactory.getProvider(request.getProviderName()))
                    .flatMap(provider -> invocationGuard.call(
                                    () -> provider.generate(toProviderRequest(request, prompt)))
                            .flatMap(raw -> assemble(spec, request, raw, prompt, startTime)))
                    .doOnError(error -> log.error("AI辅助编辑失败: action={}, userId={}, error={}",
                            request.getAction(), request.getUserId(), error.getMessage()))
                    // 失败也要记账，否则用量统计里的失败率恒为 0
                    .onErrorResume(error -> statisticsService.recordUsage(
                                    request.getUserId(),
                                    request.getProviderName() != null ? request.getProviderName() : "unknown",
                                    request.getModelName() != null ? request.getModelName() : "unknown",
                                    0, 0.0, System.currentTimeMillis() - startTime, false)
                            .then(Mono.<AiAssistResult>error(error)));
        });
    }

    @Override
    public Flux<String> assistStream(AiAssistRequest request) {
        return Flux.defer(() -> {
            ActionSpec spec = resolveAction(request.getAction());
            String prompt = buildPrompt(spec, request);

            return rateLimitService.checkAndIncrement(request.getUserId())
                    .then(providerFactory.getProvider(request.getProviderName()))
                    .flatMapMany(provider -> invocationGuard.callStream(
                            () -> provider.generateStream(toProviderRequest(request, prompt))))
                    .doOnError(error -> log.error("AI辅助编辑(流式)失败: action={}, userId={}, error={}",
                            request.getAction(), request.getUserId(), error.getMessage()));
        });
    }

    @Override
    public List<AiAssistAction> supportedActions() {
        List<AiAssistAction> list = new ArrayList<>(ACTIONS.size());
        ACTIONS.values().forEach(spec -> list.add(AiAssistAction.builder()
                .code(spec.code())
                .label(spec.label())
                .description(spec.description())
                .streaming(spec.streaming())
                .requiresContent(true)
                .build()));
        return list;
    }

    // ==================================================================
    // 内部方法
    // ==================================================================

    /**
     * 组装最终结果：解析 → 安全检查 → 记历史 → 记用量
     */
    private Mono<AiAssistResult> assemble(ActionSpec spec,
                                          AiAssistRequest request,
                                          AiGenerationResult raw,
                                          String prompt,
                                          long startTime) {

        ParsedOutput parsed = parseOutput(spec, raw.getContent());
        long generationTime = System.currentTimeMillis() - startTime;

        Integer promptTokens = raw.getPromptTokens();
        Integer completionTokens = raw.getCompletionTokens();
        Integer tokenCount = raw.getTokenCount();
        double cost = statisticsService.estimateCost(
                raw.getProviderName(), raw.getModelName(),
                promptTokens != null ? promptTokens : 0,
                completionTokens != null ? completionTokens : 0);

        AiAssistResult result = AiAssistResult.builder()
                .action(spec.code())
                .content(parsed.getContent())
                .alternatives(parsed.getAlternatives())
                .tokenCount(tokenCount)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .estimatedCost(cost)
                .providerName(raw.getProviderName())
                .modelName(raw.getModelName())
                .generationTime(generationTime)
                .build();

        // 内容安全检查：模型输出同样可能带敏感内容
        return contentSafetyService.check(result.getContent() != null ? result.getContent() : "")
                .flatMap(check -> {
                    if (!check.isSafe()) {
                        log.warn("AI辅助输出未通过安全检查: action={}, reason={}, keywords={}",
                                spec.code(), check.getReason(), check.getMatchedKeywords());
                        return Mono.error(new BusinessException(
                                "AI 返回的内容包含不安全信息，已被拒绝。原因: " + check.getReason()));
                    }
                    return saveHistory(request, prompt, result)
                            .then(recordUsage(request, result))
                            .thenReturn(result);
                });
    }

    /**
     * 保存生成历史
     */
    private Mono<Void> saveHistory(AiAssistRequest request, String prompt, AiAssistResult result) {
        try {
            String resultJson = objectMapper.writeValueAsString(result);

            AiGenerationHistory history = AiGenerationHistory.builder()
                    .userId(request.getUserId())
                    .prompt(prompt)
                    .enhancedPrompt(prompt)
                    .result(resultJson)
                    .providerName(result.getProviderName())
                    .modelName(result.getModelName())
                    .tokenCount(result.getTokenCount())
                    .cost(result.getEstimatedCost())
                    .generationTime(result.getGenerationTime())
                    .createTime(LocalDateTime.now())
                    .isDeleted(false)
                    .build();

            return historyRepository.save(history)
                    .doOnSuccess(saved -> log.debug("AI辅助历史已保存: id={}", saved.getId()))
                    .then();
        } catch (Exception e) {
            log.error("序列化AI辅助结果失败", e);
            return Mono.empty();
        }
    }

    /**
     * 记录用量
     */
    private Mono<Void> recordUsage(AiAssistRequest request, AiAssistResult result) {
        return statisticsService.recordUsage(
                request.getUserId(),
                result.getProviderName(),
                result.getModelName(),
                result.getTokenCount(),
                result.getEstimatedCost(),
                result.getGenerationTime(),
                true);
    }

    /**
     * 构造调用 Provider 的请求
     */
    private AiGenerationRequest toProviderRequest(AiAssistRequest request, String prompt) {
        return AiGenerationRequest.builder()
                .mode("free")
                .prompt(prompt)
                .language(request.getLanguage())
                .tone(request.getTone())
                .style(request.getStyle())
                .providerName(request.getProviderName())
                .modelName(request.getModelName())
                .userId(request.getUserId())
                .build();
    }

    /**
     * 构造提示词
     */
    private String buildPrompt(ActionSpec spec, AiAssistRequest request) {
        String content = request.getContent() != null ? request.getContent() : "";

        // 先替换模板自身的占位符，最后再插入正文 ——
        // 否则正文里如果恰好出现 "{count}" 这类字面量，会被当成占位符改掉
        String prompt = spec.template()
                .replace("{count}", String.valueOf(request.getCount() != null ? request.getCount() : 5))
                .replace("{length}", String.valueOf(request.getLength() != null ? request.getLength() : 500))
                .replace("{target}", targetLanguage(request.getLanguage()))
                .replace("{content}", content);

        if (request.getInstruction() != null && !request.getInstruction().isBlank()) {
            prompt = prompt + "\n\n额外要求：\n" + request.getInstruction();
        }
        return prompt;
    }

    /**
     * 翻译目标语言
     */
    private String targetLanguage(String language) {
        if (language == null || language.isBlank()) {
            return "英文";
        }
        return switch (language.toLowerCase(Locale.ROOT)) {
            case "en" -> "英文";
            case "zh" -> "中文";
            default -> language;
        };
    }

    /**
     * 解析模型输出
     */
    private ParsedOutput parseOutput(ActionSpec spec, String raw) {
        String text = raw != null ? raw.trim() : "";

        switch (spec.code()) {
            case "titles" -> {
                List<String> titles = parseTitles(text);
                if (!titles.isEmpty()) {
                    return new ParsedOutput(titles.get(0), titles);
                }
                return new ParsedOutput(text, List.of());
            }
            case "seo" -> {
                Map<String, String> seo = parseJsonObject(text);
                String title = seo.get("title");
                String description = seo.get("description");
                if (title == null && description == null) {
                    return new ParsedOutput(text, List.of());
                }
                List<String> alternatives = title != null ? List.of(title) : List.of();
                return new ParsedOutput(description != null ? description : text, alternatives);
            }
            default -> {
                return new ParsedOutput(text, List.of());
            }
        }
    }

    /**
     * 解析候选标题（一行一个）
     */
    private List<String> parseTitles(String text) {
        List<String> titles = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return titles;
        }
        for (String line : text.split("\\r?\\n")) {
            String candidate = line.trim();
            if (candidate.isEmpty()) {
                continue;
            }
            // 去掉常见的编号/项目符号前缀
            candidate = candidate.replaceFirst("^[-*•]\\s*", "")
                    .replaceFirst("^\\d+[.、)]\\s*", "")
                    .replaceAll("^[《\"']|[》\"']$", "")
                    .trim();
            if (!candidate.isEmpty() && !titles.contains(candidate)) {
                titles.add(candidate);
            }
        }
        return titles;
    }

    /**
     * 解析 JSON 对象（容忍模型输出里混入的代码块标记或前后废话）
     */
    private Map<String, String> parseJsonObject(String text) {
        Map<String, String> result = new LinkedHashMap<>();
        if (text == null || text.isEmpty()) {
            return result;
        }
        String cleaned = text.replaceAll("(?s)```[a-zA-Z]*", "").replace("```", "").trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return result;
        }
        try {
            JsonNode node = objectMapper.readTree(cleaned.substring(start, end + 1));
            if (node == null || !node.isObject()) {
                return result;
            }
            node.fields().forEachRemaining(entry -> {
                if (entry.getValue() != null && !entry.getValue().isNull()) {
                    result.put(entry.getKey(), entry.getValue().asText());
                }
            });
        } catch (Exception e) {
            log.warn("解析AI返回的JSON失败: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 解析动作
     */
    private ActionSpec resolveAction(String action) {
        if (action == null || action.isBlank()) {
            throw new BusinessException("不支持的AI操作: " + action);
        }
        ActionSpec spec = ACTIONS.get(action.trim().toLowerCase(Locale.ROOT));
        if (spec == null) {
            throw new BusinessException("不支持的AI操作: " + action);
        }
        return spec;
    }

    /**
     * 动作规格
     */
    private record ActionSpec(String code, String label, String description,
                              boolean streaming, String template) {
    }

    /**
     * 解析后的输出
     */
    private static class ParsedOutput {
        private final String content;
        private final List<String> alternatives;

        ParsedOutput(String content, List<String> alternatives) {
            this.content = content;
            this.alternatives = alternatives;
        }

        String getContent() {
            return content;
        }

        List<String> getAlternatives() {
            return alternatives;
        }
    }
}
