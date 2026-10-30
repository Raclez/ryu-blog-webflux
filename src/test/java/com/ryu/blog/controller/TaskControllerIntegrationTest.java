package com.ryu.blog.controller;

import com.ryu.blog.constant.ErrorCodeConstants;
import com.ryu.blog.entity.AsyncTask;
import com.ryu.blog.enums.TaskPriority;
import com.ryu.blog.enums.TaskStatus;
import com.ryu.blog.enums.TaskType;
import com.ryu.blog.repository.AsyncTaskRepository;
import com.ryu.blog.service.TaskQueueManager;
import com.ryu.blog.utils.JsonUtils;
import com.ryu.blog.utils.SaTokenUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TaskController 集成测试
 * 测试任务管理 API 的核心功能
 *
 * <p><b>关于登录方式</b>：Sa-Token 的 WebFlux 上下文
 * （{@code SaTokenContextForSpringReactor}）继承自
 * {@code SaTokenContextForThreadLocal}，本质上就是一个普通
 * {@code ThreadLocal<Box>}，而这个 Box 只有在
 * {@code SaReactorSyncHolder.setContext(exchange)} 之后才有值。
 * 直接在 JUnit 线程上调用 {@code StpUtil.login(id)} 必然抛
 * {@code InvalidContextException("未能获取有效的上下文")} ——
 * 它拿不到请求上下文，也就没有地方放 token。
 *
 * <p>所以这里走项目自己的 {@link SaTokenUtils#login}：它会先建立上下文再登录，
 * 和 {@code AuthController} 的登录路径完全一致。JWT 混入模式下 token 是自签的，
 * 服务端只验签不查库，因此不需要真实用户存在。
 *
 * <p><b>关于测试用户</b>：用 {@code 990001} 这个合成 id，而不是管理员 id=1。
 * 这样列表断言不依赖开发库里已有的数据，清理也只碰本测试造出来的行。
 *
 * @author ryu
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class TaskControllerIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private AsyncTaskRepository taskRepository;

    @Autowired
    private TaskQueueManager queueManager;

    /** 合成用户 id —— 不对应库里的真实用户，避免和开发数据耦合 */
    private static final Long TEST_USER_ID = 990001L;

    /** 另一个合成用户 id —— 用于「访问别人的任务」场景 */
    private static final Long OTHER_USER_ID = 990002L;

    /** 本次测试创建的任务 id —— tearDown 时按 id 精确清理 */
    private final List<Long> createdTaskIds = new ArrayList<>();

    private String authToken;

    @BeforeEach
    void setUp() {
        // 建立 Sa-Token 上下文后登录 —— 见类注释
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/auth/login").build());
        authToken = "Bearer " + SaTokenUtils.login(exchange, TEST_USER_ID).block();
    }

    @AfterEach
    void tearDown() {
        // 只清理本测试创建的任务。
        // 原先这里是 taskRepository.deleteAll().block() —— 那是把开发库里
        // 整张 t_async_tasks 清空，集成测试不该有这种杀伤力。
        deleteTasksOf(TEST_USER_ID);
        deleteTasksOf(OTHER_USER_ID);

        // 通过接口提交的任务会进 Redis 队列，一并摘掉，免得异步执行器
        // 之后又去捞这些已经不存在的任务。
        for (Long taskId : createdTaskIds) {
            queueManager.remove(String.valueOf(taskId)).block();
        }
        createdTaskIds.clear();
    }

    /**
     * 删除某个合成用户下的全部任务
     */
    private void deleteTasksOf(Long userId) {
        taskRepository.findByUserId(userId, PageRequest.of(0, 1000))
                .concatMap(taskRepository::delete)
                .then()
                .block();
    }

    /**
     * 测试提交任务接口
     * 验证：任务提交成功，返回任务ID
     */
    @Test
    void testSubmitTask_Success() {
        // 准备请求数据
        Map<String, Object> request = new HashMap<>();
        request.put("taskType", "AI_GENERATION");
        request.put("priority", "NORMAL");

        Map<String, Object> taskRequest = new HashMap<>();
        taskRequest.put("topic", "测试主题");
        taskRequest.put("keywords", "测试,关键词");
        request.put("request", taskRequest);

        // 发送请求
        byte[] responseBody = webTestClient.post()
                .uri("/api/tasks")
                .header("Authorization", authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(200)
                .jsonPath("$.data").isNotEmpty()
                .returnResult()
                .getResponseBody();

        createdTaskIds.add(readTaskId(responseBody));
    }

    /**
     * 测试查询任务状态接口
     * 验证：能够查询到已提交的任务状态
     */
    @Test
    void testGetTaskStatus_Success() {
        // 创建测试任务
        Long taskId = createTestTask(TEST_USER_ID, TaskStatus.PENDING);

        // 查询任务状态
        webTestClient.get()
                .uri("/api/tasks/{taskId}", taskId)
                .header("Authorization", authToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(200)
                .jsonPath("$.data.id").isEqualTo(taskId)
                .jsonPath("$.data.status").isEqualTo("PENDING");
    }

    /**
     * 测试权限控制
     * 验证：用户不能访问其他用户的任务
     */
    @Test
    void testGetTaskStatus_PermissionDenied() {
        // 创建其他用户的任务
        Long taskId = createTestTask(OTHER_USER_ID, TaskStatus.PENDING);

        // 尝试访问其他用户的任务（当前登录用户是TEST_USER_ID）
        webTestClient.get()
                .uri("/api/tasks/{taskId}", taskId)
                .header("Authorization", authToken)
                .exchange()
                // 归属校验在 Service 层，越权抛 PermissionDeniedException → 403
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo(ErrorCodeConstants.FORBIDDEN);
    }

    /**
     * 测试获取任务结果接口
     * 验证：只有已完成的任务才能获取结果
     */
    @Test
    void testGetTaskResult_TaskNotCompleted() {
        // 创建未完成的任务
        Long taskId = createTestTask(TEST_USER_ID, TaskStatus.PENDING);

        // 尝试获取结果
        webTestClient.get()
                .uri("/api/tasks/{taskId}/result", taskId)
                .header("Authorization", authToken)
                .exchange()
                // 项目的约定：业务异常走「HTTP 200 + 响应体里的业务错误码」，
                // 见 BusinessException 的类注释。所以这里断言 body.code，
                // 而不是 HTTP 状态码。
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(ErrorCodeConstants.OPERATION_FAILED);
    }

    /**
     * 测试获取任务结果接口 - 成功场景
     * 验证：已完成的任务可以获取结果
     */
    @Test
    void testGetTaskResult_Success() {
        // 创建已完成的任务
        Long taskId = createTestTask(TEST_USER_ID, TaskStatus.COMPLETED);

        // 获取结果
        webTestClient.get()
                .uri("/api/tasks/{taskId}/result", taskId)
                .header("Authorization", authToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(200)
                .jsonPath("$.data").isNotEmpty();
    }

    /**
     * 测试取消任务接口
     * 验证：可以取消等待中的任务
     */
    @Test
    void testCancelTask_Success() {
        // 创建等待中的任务
        Long taskId = createTestTask(TEST_USER_ID, TaskStatus.PENDING);

        // 取消任务
        webTestClient.delete()
                .uri("/api/tasks/{taskId}", taskId)
                .header("Authorization", authToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(200)
                .jsonPath("$.data").isEqualTo(true);

        // 验证任务状态已更新
        AsyncTask task = taskRepository.findById(taskId).block();
        assert task != null;
        assert task.getStatus() == TaskStatus.CANCELLED;
    }

    /**
     * 测试取消任务接口 - 不能取消已完成的任务
     * 验证：已完成的任务不能被取消
     */
    @Test
    void testCancelTask_CannotCancelCompleted() {
        // 创建已完成的任务
        Long taskId = createTestTask(TEST_USER_ID, TaskStatus.COMPLETED);

        // 尝试取消
        webTestClient.delete()
                .uri("/api/tasks/{taskId}", taskId)
                .header("Authorization", authToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(ErrorCodeConstants.OPERATION_FAILED);
    }

    /**
     * 测试重试任务接口
     * 验证：可以重试失败的任务
     */
    @Test
    void testRetryTask_Success() {
        // 创建失败的任务
        Long taskId = createTestTask(TEST_USER_ID, TaskStatus.FAILED);

        // 重试任务
        byte[] responseBody = webTestClient.post()
                .uri("/api/tasks/{taskId}/retry", taskId)
                .header("Authorization", authToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(200)
                .jsonPath("$.data").isNotEmpty()
                .returnResult()
                .getResponseBody();

        createdTaskIds.add(readTaskId(responseBody));
    }

    /**
     * 测试重试任务接口 - 不能重试非失败任务
     * 验证：只有失败的任务才能重试
     */
    @Test
    void testRetryTask_CannotRetryNonFailed() {
        // 创建成功的任务
        Long taskId = createTestTask(TEST_USER_ID, TaskStatus.COMPLETED);

        // 尝试重试
        webTestClient.post()
                .uri("/api/tasks/{taskId}/retry", taskId)
                .header("Authorization", authToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(ErrorCodeConstants.OPERATION_FAILED);
    }

    /**
     * 测试查询用户任务列表接口
     * 验证：可以分页查询用户的任务列表
     */
    @Test
    void testGetUserTasks_Success() {
        // 创建多个测试任务
        createTestTask(TEST_USER_ID, TaskStatus.PENDING);
        createTestTask(TEST_USER_ID, TaskStatus.COMPLETED);
        createTestTask(TEST_USER_ID, TaskStatus.FAILED);

        // 查询任务列表
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/tasks")
                        .queryParam("current", 1)
                        .queryParam("size", 10)
                        .build())
                .header("Authorization", authToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(200)
                // 列表接口返回的是 PageResult，数据在 records 里
                .jsonPath("$.data.records").isArray()
                .jsonPath("$.data.records.length()").isEqualTo(3)
                .jsonPath("$.data.total").isEqualTo(3);
    }

    /**
     * 测试按任务类型过滤
     * 验证：可以按任务类型过滤任务列表
     */
    @Test
    void testGetUserTasks_FilterByType() {
        // 创建不同类型的任务
        createTestTask(TEST_USER_ID, TaskStatus.PENDING, TaskType.AI_GENERATION);
        // 原先这里用的是 TaskType.EMAIL —— 那个类型没有任何 handler，
        // 已随「死任务类型清理」一起删掉了。改用 ARTICLE_PUBLISH。
        createTestTask(TEST_USER_ID, TaskStatus.PENDING, TaskType.ARTICLE_PUBLISH);

        // 查询 AI_GENERATION 类型的任务
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/tasks")
                        .queryParam("taskType", "AI_GENERATION")
                        .queryParam("current", 1)
                        .queryParam("size", 10)
                        .build())
                .header("Authorization", authToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(200)
                .jsonPath("$.data.records").isArray()
                .jsonPath("$.data.records.length()").isEqualTo(1)
                .jsonPath("$.data.records[0].taskType").isEqualTo("AI_GENERATION");
    }

    /**
     * 测试获取离线通知接口
     * 验证：可以获取离线通知列表
     */
    @Test
    void testGetOfflineNotifications_Success() {
        webTestClient.get()
                .uri("/api/tasks/notifications/offline")
                .header("Authorization", authToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(200)
                .jsonPath("$.data").isArray();
    }

    /**
     * 测试清除离线通知接口
     * 验证：可以清除离线通知
     */
    @Test
    void testClearOfflineNotifications_Success() {
        webTestClient.delete()
                .uri("/api/tasks/notifications/offline")
                .header("Authorization", authToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(200);
    }

    /**
     * 测试未认证访问
     * 验证：未认证用户不能访问任务接口
     *
     * <p>这里同时锁住「未登录要返回 401 + JSON」这个约定 ——
     * Sa-Token 的过滤器默认不设状态码、还把响应体写成纯文本，
     * 详见 {@code SaTokenConfig#renderAuthError}。
     */
    @Test
    void testUnauthorizedAccess() {
        webTestClient.get()
                .uri("/api/tasks")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo(ErrorCodeConstants.UNAUTHORIZED);
    }

    /**
     * 从响应体里读出任务ID
     */
    private Long readTaskId(byte[] responseBody) {
        Map<String, Object> body = JsonUtils.toMap(new String(responseBody, StandardCharsets.UTF_8));
        Object data = body.get("data");
        return ((Number) data).longValue();
    }

    /**
     * 创建测试任务
     */
    private Long createTestTask(Long userId, TaskStatus status) {
        return createTestTask(userId, status, TaskType.AI_GENERATION);
    }

    /**
     * 创建测试任务（指定类型）
     */
    private Long createTestTask(Long userId, TaskStatus status, TaskType taskType) {
        LocalDateTime now = LocalDateTime.now();

        AsyncTask task = AsyncTask.builder()
                .userId(userId)
                .taskType(taskType)
                .status(status)
                .priority(TaskPriority.NORMAL)
                .requestJson("{\"topic\":\"测试主题\",\"keywords\":\"测试,关键词\"}")
                .resultJson(status == TaskStatus.COMPLETED ? "{\"content\":\"测试结果\"}" : null)
                .errorMessage(status == TaskStatus.FAILED ? "测试错误" : null)
                .progress(status == TaskStatus.COMPLETED ? 100 : 0)
                .submitTime(now)
                .startTime(status != TaskStatus.PENDING ? now : null)
                .completeTime(status == TaskStatus.COMPLETED || status == TaskStatus.FAILED ? now : null)
                .retryCount(0)
                .maxRetries(3)
                .build();

        Long taskId = taskRepository.save(task)
                .map(AsyncTask::getId)
                .block();
        // 别人的任务不在清理范围内，但记下来便于需要时追溯
        if (TEST_USER_ID.equals(userId)) {
            createdTaskIds.add(taskId);
        }
        return taskId;
    }
}
