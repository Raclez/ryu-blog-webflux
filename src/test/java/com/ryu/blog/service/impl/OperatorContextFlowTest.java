package com.ryu.blog.service.impl;

import com.ryu.blog.dto.OperatorContext;
import com.ryu.blog.exception.PermissionDeniedException;
import com.ryu.blog.repository.PermissionsRepository;
import com.ryu.blog.repository.RolePermissionRepository;
import com.ryu.blog.repository.UserRoleRepository;
import com.ryu.blog.utils.OperatorContexts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Set;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 「操作者身份」从 Reactor Context 到 Service 的<b>流向</b>护栏。
 *
 * <p>改造之后，Service 方法签名里不再有 {@code OperatorContext ctx} 参数 ——
 * 身份由 {@code SaTokenAuthFilter} 写进 Reactor Context，
 * Service 用 {@code Mono.deferContextual(...)} 取。
 *
 * <p>这件事一旦接错，后果是<b>静默的越权</b>：取不到身份就退化成匿名，
 * 而匿名者只能操作自己 —— 于是一律 403（安全，但功能全废）；
 * 更糟的是取到<b>别人的</b>身份。所以这里把三条流向都钉住：
 * 本人放行、无权限者拒绝、<b>没有身份也拒绝</b>（fail-closed）。
 *
 * @author ryu
 */
class OperatorContextFlowTest {

    private static final Long TARGET_USER_ID = 8801L;
    private static final Long OTHER_USER_ID = 8802L;

    private final UserPermissionResolver permissionResolver = mock(UserPermissionResolver.class);
    private final UserPermissionCache permissionCache = mock(UserPermissionCache.class);

    private PermissionsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PermissionsServiceImpl(
                mock(PermissionsRepository.class),
                mock(RolePermissionRepository.class),
                mock(UserRoleRepository.class),
                permissionResolver,
                permissionCache,
                mock(R2dbcEntityTemplate.class));

        when(permissionResolver.resolvePermissionIdentities(anyLong())).thenReturn(Mono.just(Set.of()));
    }

    @Test
    @DisplayName("身份在 Reactor Context 里 → Service 读到它：查自己的权限放行")
    void ownUser_isAllowed() {
        StepVerifier.create(service.getUserPermissionIdentities(TARGET_USER_ID)
                        .contextWrite(ctx -> ctx.put(OperatorContexts.KEY,
                                new OperatorContext(TARGET_USER_ID, false))))
                .expectNext(Set.of())
                .verifyComplete();
    }

    @Test
    @DisplayName("身份在 Reactor Context 里 → Service 读到它：查别人的权限，无管理能力则拒绝")
    void otherUserWithoutManageCapability_isDenied() {
        StepVerifier.create(service.getUserPermissionIdentities(TARGET_USER_ID)
                        .contextWrite(ctx -> ctx.put(OperatorContexts.KEY,
                                new OperatorContext(OTHER_USER_ID, false))))
                .expectError(PermissionDeniedException.class)
                .verify();
    }

    @Test
    @DisplayName("身份在 Reactor Context 里 → Service 读到它：有管理能力则放行")
    void otherUserWithManageCapability_isAllowed() {
        StepVerifier.create(service.getUserPermissionIdentities(TARGET_USER_ID)
                        .contextWrite(ctx -> ctx.put(OperatorContexts.KEY,
                                new OperatorContext(OTHER_USER_ID, true))))
                .expectNext(Set.of())
                .verifyComplete();
    }

    @Test
    @DisplayName("Reactor Context 里没有身份 → 退化成匿名 → 拒绝（fail-closed，不放行）")
    void withoutOperator_isDenied() {
        StepVerifier.create(service.getUserPermissionIdentities(TARGET_USER_ID))
                .expectError(PermissionDeniedException.class)
                .verify();
    }
}
