package com.ryu.blog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * 分页入参护栏。
 *
 * <p>本项目里有两类被反复踩到的坑，这一组用例把它们钉死：
 *
 * <ol>
 *   <li><b>分页参数没有上下界</b> —— {@code pageSize} 没传就是 0，
 *       {@code PageRequest.of(page, 0)} 抛
 *       {@code IllegalArgumentException: Page size must not be less than one} → 500；
 *       传一个巨大的值则会把整表拉进内存。</li>
 *   <li><b>约束写了但没生效</b> —— DTO 上加了 {@code @Min/@Max}，
 *       Controller 的 {@code @ParameterObject} 参数却漏了 {@code @Valid}，
 *       于是约束形同虚设（Spring 不会对未开启校验的参数做绑定后校验）。</li>
 * </ol>
 */
class PaginationDtoBoundsTest {

    /**
     * 所有以分页方式对外暴露的查询 DTO。
     */
    private static final List<Class<?>> PAGED_DTOS = List.of(
            CategoryListDTO.class,
            TagListDTO.class,
            PermissionsQueryDTO.class,
            SysDictItemDTO.class,
            SysDictTypeQueryDTO.class,
            PostQueryDTO.class,
            RoleListDTO.class,
            UserListDTO.class);

    /** 页码字段的候选名（不同 DTO 的命名不统一）。 */
    private static final List<String> PAGE_FIELDS = List.of("currentPage", "current");

    /** 每页条数字段的候选名。 */
    private static final List<String> SIZE_FIELDS = List.of("pageSize", "size");

    @Test
    @DisplayName("分页 DTO 的页码/每页条数都要有下界与上界")
    void pagedDtosAreBounded() {
        for (Class<?> dto : PAGED_DTOS) {
            assertBounded(dto, PAGE_FIELDS);
            assertBounded(dto, SIZE_FIELDS);
        }
    }

    @Test
    @DisplayName("@ParameterObject 的 Controller 参数必须带 @Valid/@Validated，否则约束不生效")
    void parameterObjectsAreValidated() {
        List<String> missing = new ArrayList<>();

        List<Class<?>> controllers = scanControllers();
        // 扫描器本身必须真的扫到了东西，否则这个用例是空转的假绿
        if (controllers.isEmpty()) {
            fail("没有扫描到任何 Controller，扫描逻辑坏了");
        }

        for (Class<?> controller : controllers) {
            for (Method method : controller.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(RequestMapping.class)) {
                    continue;
                }
                for (Parameter parameter : method.getParameters()) {
                    if (!parameter.isAnnotationPresent(ParameterObject.class)) {
                        continue;
                    }
                    boolean validated = parameter.isAnnotationPresent(Valid.class)
                            || parameter.isAnnotationPresent(Validated.class)
                            || method.isAnnotationPresent(Validated.class);
                    if (!validated) {
                        missing.add(controller.getSimpleName() + "#" + method.getName()
                                + " → " + parameter.getType().getSimpleName());
                    }
                }
            }
        }

        if (!missing.isEmpty()) {
            fail("以下 @ParameterObject 参数没有开启校验，DTO 上的约束不会生效：" + missing);
        }
    }

    @Test
    @DisplayName("分页 DTO 的页码/每页条数要么有默认值，要么标注 @NotNull")
    void pagedDtosHaveDefaults() {
        for (Class<?> dto : PAGED_DTOS) {
            assertHasDefaultOrNotNull(dto, PAGE_FIELDS);
            assertHasDefaultOrNotNull(dto, SIZE_FIELDS);
        }
    }

    private void assertBounded(Class<?> dto, List<String> fieldNames) {
        Field field = findField(dto, fieldNames);
        if (field == null) {
            return;
        }
        Min min = field.getAnnotation(Min.class);
        Max max = field.getAnnotation(Max.class);
        if (min == null || min.value() < 1) {
            fail(dto.getSimpleName() + "#" + field.getName() + " 缺少 @Min(1)（页码/页长不能为 0 或负数）");
        }
        if (max == null) {
            fail(dto.getSimpleName() + "#" + field.getName() + " 缺少 @Max 上界（否则能一次拉全表）");
        }
    }

    private void assertHasDefaultOrNotNull(Class<?> dto, List<String> fieldNames) {
        Field field = findField(dto, fieldNames);
        if (field == null) {
            return;
        }
        if (field.isAnnotationPresent(NotNull.class)) {
            // 明确要求必填也算一种约束，交给 @Valid 挡在 Controller 层
            return;
        }
        Object value = defaultValueOf(dto, field);
        if (value == null) {
            fail(dto.getSimpleName() + "#" + field.getName()
                    + " 既没有默认值也没有 @NotNull，客户端漏传就是 null");
        }
    }

    private Field findField(Class<?> type, List<String> names) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (String name : names) {
                try {
                    return c.getDeclaredField(name);
                } catch (NoSuchFieldException ignored) {
                    // 继续往父类/下一个候选名找
                }
            }
        }
        return null;
    }

    private Object defaultValueOf(Class<?> dto, Field field) {
        try {
            Object instance = dto.getDeclaredConstructor().newInstance();
            field.setAccessible(true);
            return field.get(instance);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private List<Class<?>> scanControllers() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(Object.class));
        Set<BeanDefinition> candidates =
                scanner.findCandidateComponents("com.ryu.blog.controller");
        List<Class<?>> result = new ArrayList<>();
        for (BeanDefinition definition : candidates) {
            try {
                Class<?> clazz = Class.forName(definition.getBeanClassName());
                if (clazz.isAnnotationPresent(RestController.class) || hasRequestMapping(clazz)) {
                    result.add(clazz);
                }
            } catch (ClassNotFoundException ignored) {
                // 忽略无法加载的类
            }
        }
        return result;
    }

    private boolean hasRequestMapping(Class<?> clazz) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(RequestMapping.class)) {
                return true;
            }
        }
        return clazz.getAnnotation(RequestMapping.class) != null;
    }
}
