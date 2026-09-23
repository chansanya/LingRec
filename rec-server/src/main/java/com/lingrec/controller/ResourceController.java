package com.lingrec.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingrec.service.ResourceService;
import com.lingrec.starter.entity.Category;
import com.lingrec.starter.entity.Resource;
import com.lingrec.starter.mapper.CategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 资源与分类元数据查询控制器。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ResourceController {
    private final ResourceService resourceService;
    private final CategoryMapper categoryMapper;

    /**
     * 按可选分类范围查询资源，并组装前端所需的父子分类信息。
     *
     * @param categoryId 可选分类主键；为空查询全部，父分类覆盖其子分类，子分类仅查询自身
     * @return 符合分类范围的资源展示数据
     */
    @GetMapping("/resources")
    public List<Map<String, Object>> getResources(@RequestParam(required = false) Long categoryId) {
        List<Resource> resources = resourceService.getByCategoryScope(categoryId);
        Map<Long, Category> categoryMap = categoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(Category::getId, category -> category));

        return resources.stream()
                .map(resource -> toResourceMap(resource, categoryMap))
                .collect(Collectors.toList());
    }

    /**
     * 查询全部分类并构建父分类及其子分类组成的两级分类树。
     *
     * @return 按排序字段和主键升序排列的分类树
     */
    @GetMapping("/categories")
    public List<Map<String, Object>> getCategories() {
        List<Category> categories = categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                .orderByAsc(Category::getSortOrder)
                .orderByAsc(Category::getId));
        Map<Long, List<Category>> childrenMap = categories.stream()
                .filter(category -> category.getParentId() != null)
                .collect(Collectors.groupingBy(Category::getParentId));

        return categories.stream()
                .filter(category -> category.getParentId() == null)
                .map(parent -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("id", parent.getId());
                    result.put("name", parent.getName());
                    result.put("icon", parent.getIcon());
                    result.put("children", childrenMap.getOrDefault(parent.getId(), Collections.emptyList()));
                    return result;
                })
                .collect(Collectors.toList());
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
