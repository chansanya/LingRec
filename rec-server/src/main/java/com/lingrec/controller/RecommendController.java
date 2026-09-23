package com.lingrec.controller;

import com.lingrec.core.model.RecommendPageResult;
import com.lingrec.core.model.ResourceItem;
import com.lingrec.starter.template.LingRecTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 推荐接口控制层，面向演示操作面板提供推荐会话初始化与游标翻页。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RecommendController {
    private final LingRecTemplate lingRecTemplate;

    /**
     * 为用户创建推荐会话并返回第一页推荐结果。
     *
     * @param userId 用户主键
     * @param categoryId 可选分类主键；为空查询全部
     * @return 包含会话标识、资源列表、游标和总数量的推荐首页
     */
    @GetMapping("/recommend/{userId}")
    public Map<String, Object> getRecommendations(
            @PathVariable Long userId,
            @RequestParam(required = false) Long categoryId) {
        RecommendPageResult page = lingRecTemplate.getRecommendations(userId, categoryId);
        return toPageMap(page);
    }

    /**
     * 按会话游标读取推荐快照的下一页。
     *
     * @param userId 用户主键
     * @param sessionId 推荐会话标识
     * @param cursor 当前已加载条数，从 0 开始
     * @param size 本次加载条数，默认 20，最大 100
     * @return 包含资源列表、游标和是否还有更多数据的推荐分页
     */
    @GetMapping("/recommend/{userId}/page")
    public Map<String, Object> getRecommendationPage(
            @PathVariable Long userId,
            @RequestParam String sessionId,
            @RequestParam(defaultValue = "0") int cursor,
            @RequestParam(defaultValue = "20") int size) {
        RecommendPageResult page = lingRecTemplate.getRecommendationPage(userId, sessionId, cursor, size);
        return toPageMap(page);
    }

    /**
     * 将推荐分页结果转换为前端所需响应结构。
     *
     * @param page 推荐分页结果
     * @return 可序列化为 JSON 的响应数据
     */
    private Map<String, Object> toPageMap(RecommendPageResult page) {
        List<Map<String, Object>> items = page.getItems().stream()
                .map(this::toResourceMap)
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", page.getSessionId());
        result.put("items", items);
        result.put("nextCursor", page.getNextCursor());
        result.put("hasMore", page.isHasMore());
        result.put("total", page.getTotal());
        return result;
    }

    /**
     * 将 ResourceItem 传输对象映射为前端所需的键值结构。
     *
     * @param item 资源模型
     * @return 资源键值数据
     */
    private Map<String, Object> toResourceMap(ResourceItem item) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", item.getId());
        result.put("title", item.getTitle());
        result.put("description", item.getDescription());
        result.put("categoryId", item.getCategoryId());
        result.put("categoryName", item.getCategoryName());
        result.put("parentCategoryName", item.getParentCategoryName());
        result.put("coverUrl", item.getCoverUrl());
        result.put("heat", item.getHeat());
        result.put("createdAt", item.getCreatedAt());
        return result;
    }
}
