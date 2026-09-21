package com.lingrec.controller;

import com.lingrec.mapper.CategoryMapper;
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
     * 获取指定用户在当前分类范围内的推荐资源，并补充分类展示信息。
     *
     * @param userId 用户主键
     * @param categoryId 可选分类主键；为空查询全部，父分类覆盖其子分类，子分类仅查询自身
     * @return 按推荐算法顺序排列的资源展示数据
     */
    @GetMapping("/recommend/{userId}")
    public List<Map<String, Object>> getRecommendations(
            @PathVariable Long userId,
            @RequestParam(required = false) Long categoryId) {
        List<Resource> recommendations = recommendService.getRecommendations(userId, categoryId);
        Map<Long, Category> categoryMap = categoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(Category::getId, category -> category));

        return recommendations.stream()
                .map(resource -> toResourceMap(resource, categoryMap))
                .collect(Collectors.toList());
    }

    /**
     * 将资源实体转换为前端响应结构，并补充父分类和子分类名称。
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
