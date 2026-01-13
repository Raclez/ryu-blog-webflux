package com.ryu.blog.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.ryu.blog.entity.AsyncTask;
import com.ryu.blog.enums.TaskPriority;
import com.ryu.blog.enums.TaskStatus;
import com.ryu.blog.enums.TaskType;
import com.ryu.blog.repository.AsyncTaskRepository;
import com.ryu.blog.service.TaskQueueManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * TaskController 集成测试
 * 测试任务管理 API 的核心功能
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

    private static final Long TEST_USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private String authToken;

    @BeforeEach
    void setUp() {
        // 模拟用户登录
        StpUtil.login(TEST_USER_ID);
        authToken = "Bearer " + StpUtil.getTokenValue();
    }

    @AfterEach
    void tearDown() {
        // 清理测试数据
        taskRepository.deleteAll().block();
        StpUtil.logout();
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
        webTestClient.post()
                .uri("/api/tasks")
                .header("Authorization", authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(200)
                .jsonPath("$.data").isNotEmpty();
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
                .expectStatus().is4xxClientError();
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
                .expectStatus().is4xxClientError();
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
                .expectStatus().is4xxClientError();
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
        webTestClient.post()
                .uri("/api/tasks/{taskId}/retry", taskId)
                .header("Authorization", authToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(200)
                .jsonPath("$.data").isNotEmpty();
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
                .expectStatus().is4xxClientError();
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
                        .queryParam("page", 0)
                        .queryParam("size", 10)
                        .build())
                .header("Authorization", authToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(200)
                .jsonPath("$.data").isArray()
                .jsonPath("$.data.length()").isEqualTo(3);
    }

    /**
     * 测试按任务类型过滤
     * 验证：可以按任务类型过滤任务列表
     */
    @Test
    void testGetUserTasks_FilterByType() {
        // 创建不同类型的任务
        createTestTask(TEST_USER_ID, TaskStatus.PENDING, TaskType.AI_GENERATION);
        createTestTask(TEST_USER_ID, TaskStatus.PENDING, TaskType.EMAIL);

        // 查询 AI_GENERATION 类型的任务
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/tasks")
                        .queryParam("taskType", "AI_GENERATION")
                        .queryParam("page", 0)
                        .queryParam("size", 10)
                        .build())
                .header("Authorization", authToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(200)
                .jsonPath("$.data").isArray()
                .jsonPath("$.data.length()").isEqualTo(1)
                .jsonPath("$.data[0].taskType").isEqualTo("AI_GENERATION");
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
     */
    @Test
    void testUnauthorizedAccess() {
        webTestClient.get()
                .uri("/api/tasks")
                .exchange()
                .expectStatus().isUnauthorized();
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

        return taskRepository.save(task)
                .map(AsyncTask::getId)
                .block();
    }
}
