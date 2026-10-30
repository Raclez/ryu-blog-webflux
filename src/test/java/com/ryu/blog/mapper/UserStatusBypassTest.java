package com.ryu.blog.mapper;

import com.ryu.blog.dto.UserDTO;
import com.ryu.blog.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「改资料」不能顺带改掉账号状态 —— 权限绕过的回归护栏。
 *
 * <p><b>被守住的那道门</b>：
 * 账号状态（禁用/锁定）走专用的 {@code PUT /users/{id}/status/{status}}，
 * 那道端点有独立的 {@code system:user:status} 权限。
 * 而 {@code PUT /users/edit/{id}} 只要 {@code system:user:edit}。
 *
 * <p><b>原来的漏洞</b>：{@code UserDTO} 里有 {@code status} 字段，
 * 而 MapStruct 会<b>自动映射同名字段</b> —— 于是
 * {@code updateUserFromDTO} 会把请求体里的 {@code status} 抄进实体，
 * 持有「改资料」权限的人就能禁用/解禁任意账号（甚至给自己解禁），
 * 把 {@code system:user:status} 这道闸门整个绕过去。
 *
 * <p>三重护栏（任何一条被拆掉都要重新补上）：
 * <ol>
 *   <li>{@code UserDTO} 里根本没有 {@code status} / {@code lastLogin} / {@code lastLoginIp}；</li>
 *   <li>映射器上显式 {@code @Mapping(ignore = true)} —— 即使将来 DTO 又加回这个字段，
 *       也不会被自动映射；</li>
 *   <li>{@code updateUserFromDTO} 走 {@code IGNORE} 策略，
 *       实体上原有的 {@code status} 会被原样保留。</li>
 * </ol>
 */
class UserStatusBypassTest {

    private final UserMapper userMapper = new UserMapperImpl();

    @Test
    @DisplayName("UserDTO 不暴露 status / lastLogin / lastLoginIp")
    void dtoDoesNotExposeServerControlledFields() {
        Set<String> properties = propertyNames(UserDTO.class);

        assertFalse(properties.contains("status"),
                "UserDTO 不能有 status —— 状态有专门的端点和 system:user:status 权限");
        assertFalse(properties.contains("lastLogin"),
                "UserDTO 不能有 lastLogin —— 登录痕迹由服务端写");
        assertFalse(properties.contains("lastLoginIp"),
                "UserDTO 不能有 lastLoginIp —— 登录痕迹由服务端写");
    }

    @Test
    @DisplayName("toUser：即使 DTO 里塞了状态，映射出来的实体也不会带上")
    void toUserDoesNotCarryStatus() {
        UserDTO dto = new UserDTO();
        dto.setUsername("someone");
        dto.setNickname("昵称");

        User user = userMapper.toUser(dto);

        assertNotNull(user);
        assertEquals("someone", user.getUsername());
        assertNull(user.getStatus(), "新用户的 status 必须由服务端决定，而不是来自请求体");
        assertNull(user.getLastLoginIp());
        assertNull(user.getLastLoginTime());
    }

    @Test
    @DisplayName("updateUserFromDTO：实体原有的 status 被原样保留")
    void updateUserFromDTOPreservesStatus() {
        User existing = new User();
        existing.setId(42L);
        existing.setUsername("target");
        existing.setStatus(1);
        existing.setLastLoginIp("10.0.0.1");

        UserDTO dto = new UserDTO();
        dto.setId(42L);
        dto.setUsername("target-renamed");
        dto.setNickname("新昵称");

        User updated = userMapper.updateUserFromDTO(dto, existing);

        assertEquals(1, updated.getStatus(), "「改资料」不能改账号状态");
        assertEquals("10.0.0.1", updated.getLastLoginIp(), "「改资料」不能改登录痕迹");
        assertEquals("target-renamed", updated.getUsername());
        assertEquals("新昵称", updated.getNickname());
    }

    @Test
    @DisplayName("updateUserFromDTO：禁用中的账号（status=0）也不会被「改资料」顺手启用")
    void updateUserFromDTODoesNotReEnableDisabledAccount() {
        User disabled = new User();
        disabled.setId(7L);
        disabled.setStatus(0);

        UserDTO dto = new UserDTO();
        dto.setId(7L);
        dto.setNickname("还是叫这个名字");

        User updated = userMapper.updateUserFromDTO(dto, disabled);

        assertEquals(0, updated.getStatus(),
                "禁用中的账号只能通过 system:user:status 端点恢复");
    }

    /**
     * 取出类的全部字段名（含继承来的）
     */
    private static Set<String> propertyNames(Class<?> type) {
        Set<String> names = new java.util.HashSet<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            Arrays.stream(c.getDeclaredFields())
                    .map(java.lang.reflect.Field::getName)
                    .forEach(names::add);
        }
        return names;
    }
}
