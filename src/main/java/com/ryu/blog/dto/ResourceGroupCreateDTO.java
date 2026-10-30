package com.ryu.blog.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 资源组创建数据传输对象
 *
 * @author ryu
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResourceGroupCreateDTO {
    
    /**
     * 资源组名称
     */
    @NotBlank(message = "资源组名称不能为空")
    @Size(min = 1, max = 100, message = "资源组名称长度应在1-100之间")
    private String groupName;
    
    /**
     * 资源组描述
     */
    @Size(max = 255, message = "资源组描述长度不能超过255")
    private String description;

    /**
     * 排序号
     *
     * <p>原先这个字段名拼成了 {@code staus}，与实体里的 {@code sort} 对不上，
     * MapStruct 不会自动映射，导致客户端传的排序值被静默丢弃、{@code sort} 列永远是 NULL。
     * 这里改回正确名字，同时用 {@code @JsonAlias} 保留旧的 JSON 键以兼容存量客户端。
     */
    @JsonAlias("staus")
    @Schema(description = "排序号", example = "0")
    private Integer sort;

} 