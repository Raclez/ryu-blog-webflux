package com.ryu.blog.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 资源分组里的文件条目。
 *
 * <p><b>为什么不用 {@code File} 实体</b>：实体是<b>存储层</b>的形状 ——
 * 里面有校验和、分享凭证、存储类型这类「内部字段」。
 * 早先的分组列表直接返回 {@code PageResult<File>}，
 * 于是每加一个实体字段，接口就自动多暴露一个字段：
 * 实体字段增删是「加一列」的成本，而对外契约的变化应该是显式的。
 *
 * <p>本 VO 只列<b>列表页要展示</b>的字段，其余一律不出现在响应里。
 *
 * <p><b>关于 {@code filePath}</b>：这里放的是<b>可直接访问的公开 URL</b>
 * （不是存储侧的相对路径）—— 前端把它当 {@code <img src>} 用。
 * 名字与语义对不上是历史包袱，改名会牵动前端，单独记一笔。
 *
 * @author ryu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileGroupItemVO {

    /**
     * 文件ID
     */
    private Long id;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 可直接访问的公开URL（不是存储侧路径）
     */
    private String filePath;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 格式化后的文件大小
     */
    private String formattedSize;

    /**
     * 文件类型（image / video / text ...）
     */
    private String fileType;

    /**
     * MIME类型
     */
    private String mimeType;

    /**
     * 上传用户ID
     */
    private Long creatorId;

    /**
     * 上传用户名称
     */
    private String creatorName;

    /**
     * 文件状态
     */
    private Integer status;

    /**
     * 访问类型：0-公开，1-私有（2/3 暂按私有处理）
     */
    private Integer accessType;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
