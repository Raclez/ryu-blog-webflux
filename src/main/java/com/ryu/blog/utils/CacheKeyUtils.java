package com.ryu.blog.utils;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 缓存 key 构造工具
 *
 * <p>缓存 key 必须<b>无歧义</b>地由方法入参推导出来 —— 两个不同的入参组合
 * 算出同一个 key，就会把 A 的结果当成 B 的返回，这是最隐蔽的一类缓存 bug。
 *
 * @author ryu
 */
public final class CacheKeyUtils {

    private CacheKeyUtils() {
    }

    /**
     * 把一组 ID 规范化成一个稳定的 key 片段。
     *
     * <p><b>为什么不能用 {@code List.hashCode()}</b>：{@code List.hashCode()} 是
     * 「逐个元素的 hash 相加」，不同列表很容易撞出同一个值
     * （例如 {@code [1, 2]} 与 {@code [3, 0]} 的 hashCode 相同）。
     * 用它当缓存 key，撞上的那次请求就会拿到<b>另一个批次</b>的文件信息 ——
     * 数据串号，而且极难复现。
     *
     * <p>本方法先排序再拼接，因此：
     * <ul>
     *   <li>不同 ID 集合一定得到不同 key（无碰撞）；</li>
     *   <li>同一批 ID 无论顺序如何都得到同一个 key（顺序无关的查询可以共用缓存）。</li>
     * </ul>
     *
     * @param ids ID 集合，可为 {@code null} 或空
     * @return 形如 {@code "1,2,3"} 的 key 片段；入参为空时返回空串
     */
    public static String ofIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }
}
