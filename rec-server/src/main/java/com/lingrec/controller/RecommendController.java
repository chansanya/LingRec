package com.lingrec.controller;

import com.lingrec.mapper.CategoryMapper;
import com.lingrec.model.dto.RecommendPageResult;
import com.lingrec.model.entity.Category;
import com.lingrec.model.entity.Resource;
import com.lingrec.service.RecommendService;
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

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RecommendController {
    private final RecommendService recommendService;
    private final CategoryMapper categoryMapper;

    /**
     * 为用户创建推荐会话并返回第一页推荐结果。
     *
     * @param userId 用户主键
     * @param categoryId 可选分类主键；为空查询全部，父分类覆盖其子分类，子分类仅查询自身
     * @return 包含会话标识、资源列表、游标和总数量的推荐首页
     */
    @GetMapping("/recommend/{userId}")
    public Map<String, Object> getRecommendations(
            @PathVariable Long userId,
            @RequestParam(required = false) Long categoryId) {
        RecommendPageResult page = recommendService.createRecommendationSession(userId, categoryId);
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
        RecommendPageResult page = recommendService.getRecommendationPage(userId, sessionId, cursor, size);
        return toPageMap(page);
    }

    /**
     * 将推荐分页结果转换为前端响应结构，并补充父子分类信息。
     *
     * @param page 推荐分页结果
     * @return 可序列化为 JSON 的响应数据
     */
    private Map<String, Object> toPageMap(RecommendPageResult page) {
        Map<Long, Category> categoryMap = categoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(Category::getId, category -> category));

        List<Map<String, Object>> items = page.getItems().stream()
                .map(resource -> toResourceMap(resource, categoryMap))
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
     * 将资源实体转换为包含父子分类名称的接口响应结构。
     *
     * @param resource 待转换的资源实体
     * @param categoryMap 以分类 ID 为键的完整分类映射
     * @return 可直接序列化为 JSON 的资源展示数据
     */
    private Map<String, Object> toResourceMap(Resource resource, Map<Long, Category> categoryMap) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", resource.getId());
        result.put("title", resource.getTitle());
        result.put("description", resource.getDescription());
        result.put("categoryId", resource.getCategoryId());
        result.put("coverUrl", resource.getCoverUrl());
        result.put("heat", resource.getHeat());
        result.put("createdAt", resource.getCreatedAt());

        Category category = categoryMap.get(resource.getCategoryId());
        if (category != null) {
            result.put("categoryName", category.getName());
            Category parent = categoryMap.get(category.getParentId());
            result.put("parentCategoryName", parent != null ? parent.getName() : null);
        }
        return result;
    }
}
