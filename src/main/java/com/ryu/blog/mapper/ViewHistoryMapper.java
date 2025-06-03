package com.ryu.blog.mapper;

import com.ryu.blog.dto.ViewHistoryDTO;
import com.ryu.blog.entity.Posts;
import com.ryu.blog.entity.ViewHistory;
import com.ryu.blog.vo.ViewHistoryPageVO;
import com.ryu.blog.vo.ViewHistoryVO;
import org.mapstruct.*;

import java.util.List;

/**
 * 浏览历史实体映射器
 * 
 * @author ryu 475118582@qq.com
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ViewHistoryMapper {
    
    /**
     * 将ViewHistory实体转换为ViewHistoryVO
     * 
     * @param viewHistory 浏览历史实体
     * @return ViewHistoryVO
     */
    ViewHistoryVO toViewHistoryVO(ViewHistory viewHistory);
    
    /**
     * 将ViewHistory实体和文章信息转换为ViewHistoryVO
     * 
     * @param viewHistory 浏览历史实体
     * @param posts 文章实体
     * @return ViewHistoryVO
     */
    @Mapping(target = "id", source = "viewHistory.id")
    @Mapping(target = "postId", source = "viewHistory.postId")
    @Mapping(target = "visitorId", source = "viewHistory.visitorId")
    @Mapping(target = "viewTime", source = "viewHistory.viewTime")
    @Mapping(target = "ipAddress", source = "viewHistory.ipAddress")
    @Mapping(target = "agent", source = "viewHistory.agent")
    @Mapping(target = "location", source = "viewHistory.location")
    @Mapping(target = "viewDuration", source = "viewHistory.viewDuration")
    @Mapping(target = "referer", source = "viewHistory.referer")
    @Mapping(target = "postTitle", source = "posts.title")
    @Mapping(target = "coverImageId", source = "posts.coverImageId")
    ViewHistoryVO toViewHistoryVO(ViewHistory viewHistory, Posts posts);
    
    /**
     * 将ViewHistoryDTO转换为ViewHistory实体
     * 
     * @param dto 浏览历史DTO
     * @return ViewHistory
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "viewTime", ignore = true)
    @Mapping(target = "ipAddress", ignore = true)
    @Mapping(target = "agent", ignore = true)
    @Mapping(target = "location", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    ViewHistory toViewHistory(ViewHistoryDTO dto);
    
    /**
     * 构建分页结果
     * 
     * @param records 记录列表
     * @param current 当前页码
     * @param size 每页大小
     * @param total 总记录数
     * @param pages 总页数
     * @return ViewHistoryPageVO
     */
    @Mapping(target = "records", source = "records")
    @Mapping(target = "current", source = "current")
    @Mapping(target = "size", source = "size")
    @Mapping(target = "total", source = "total")
    @Mapping(target = "pages", source = "pages")
    @Mapping(target = "hasPrevious", expression = "java(current > 1)")
    @Mapping(target = "hasNext", expression = "java(current < pages)")
    ViewHistoryPageVO toPageVO(List<ViewHistoryVO> records, Long current, Long size, Long total, Long pages);
} 