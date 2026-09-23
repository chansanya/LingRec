package com.lingrec.starter.provider;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingrec.core.model.ResourceItem;
import com.lingrec.core.spi.ResourceItemProvider;
import com.lingrec.starter.entity.Category;
import com.lingrec.starter.entity.Resource;
import com.lingrec.starter.mapper.CategoryMapper;
import com.lingrec.starter.mapper.ResourceMapper;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 默认基于 MyBatis-Plus 操作内置 category 和 resource 标准表的资源数据提供者实现。
 */
@RequiredArgsConstructor
public class DefaultResourceItemProvider implements ResourceItemProvider {
    private final ResourceMapper resourceMapper;
    private final CategoryMapper categoryMapper;

    /**
     * 解析给定分类标识所涵盖的所有有效子分类主键集合。
     *
     * @param categoryId 可选分类主键，为空表示全部分类
     * @return 分类主键集合
     */
    @Override
    public List<Long> resolveCategoryIds(Long categoryId) {
        if (categoryId == null) {
            return categoryMapper.selectList(null).stream()
                    .map(Category::getId)
                    .collect(Collectors.toList());
        }

        Category category = categoryMapper.selectById(categoryId);
        if (category == null) {
            return Collections.emptyList();
        }

        if (category.getParentId() != null) {
            return Collections.singletonList(categoryId);
        }

        List<Long> categoryIds = categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                        .eq(Category::getParentId, categoryId))
                .stream()
                .map(Category::getId)
                .collect(Collectors.toCollection(ArrayList::new));
        categoryIds.add(categoryId);
        return categoryIds;
    }

    /**
     * 召回热门候选资源并封装为统一 ResourceItem。
     *
     * @param categoryIds 目标分类主键集合，为空表示全部分类
     * @param limit 最大返回条数
     * @return 热门候选资源列表
     */
    @Override
    public List<ResourceItem> getHotCandidates(List<Long> categoryIds, int limit) {
        if (limit <= 0) return Collections.emptyList();
        List<Resource> list = resourceMapper.selectList(Wrappers.<Resource>lambdaQuery()
                .in(categoryIds != null && !categoryIds.isEmpty(), Resource::getCategoryId, categoryIds)
                .orderByDesc(Resource::getHeat)
                .orderByDesc(Resource::getId)
                .last("LIMIT " + limit));
        return toResourceItems(list);
    }

    /**
     * 召回新鲜候选资源并封装为统一 ResourceItem。
     *
     * @param categoryIds 目标分类主键集合，为空表示全部分类
     * @param limit 最大返回条数
     * @return 新鲜候选资源列表
     */
    @Override
    public List<ResourceItem> getFreshCandidates(List<Long> categoryIds, int limit) {
        if (limit <= 0) return Collections.emptyList();
        List<Resource> list = resourceMapper.selectList(Wrappers.<Resource>lambdaQuery()
                .in(categoryIds != null && !categoryIds.isEmpty(), Resource::getCategoryId, categoryIds)
                .orderByDesc(Resource::getCreatedAt)
                .orderByDesc(Resource::getId)
                .last("LIMIT " + limit));
        return toResourceItems(list);
    }

    /**
     * 召回兴趣分类候选资源并封装为统一 ResourceItem。
     *
     * @param interestCategoryIds 经过交集收敛后的目标兴趣分类主键列表
     * @param limit 最大返回条数
     * @return 兴趣候选资源列表
     */
    @Override
    public List<ResourceItem> getInterestCandidates(List<Long> interestCategoryIds, int limit) {
        if (interestCategoryIds == null || interestCategoryIds.isEmpty() || limit <= 0) {
            return Collections.emptyList();
        }
        List<Resource> list = resourceMapper.selectList(Wrappers.<Resource>lambdaQuery()
                .in(Resource::getCategoryId, interestCategoryIds)
                .orderByDesc(Resource::getHeat)
                .orderByDesc(Resource::getId)
                .last("LIMIT " + limit));
        return toResourceItems(list);
    }

    /**
     * 根据主键批量拉取资源展示详情。
     *
     * @param resourceIds 资源主键集合
     * @return 批量资源展示模型列表
     */
    @Override
    public List<ResourceItem> getByIds(List<Long> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Resource> list = resourceMapper.selectBatchIds(resourceIds);
        return toResourceItems(list);
    }

    /**
     * 更新内置 resource 表中的资源热度值，确保下限不小于 0。
     *
     * @param resourceId 资源主键 ID
     * @param delta 热度变化量（正数累加，负数扣减）
     * @return 更新后的最新热度分值
     */
    @Override
    public int updateHeat(Long resourceId, int delta) {
        if (resourceId == null) return 0;
        Resource resource = resourceMapper.selectById(resourceId);
        if (resource == null) return 0;

        int currentHeat = resource.getHeat() == null ? 0 : resource.getHeat();
        int newHeat = Math.max(0, currentHeat + delta);
        if (delta != 0) {
            resource.setHeat(newHeat);
            resourceMapper.updateById(resource);
        }
        return newHeat;
    }

    /**
     * 根据分类 ID 查询分类及父分类名称元数据。
     *
     * @param categoryId 分类主键 ID
     * @return 包含分类及父分类名称的 ResourceItem 载体
     */
    @Override
    public ResourceItem getCategoryMetadata(Long categoryId) {
        if (categoryId == null) return null;
        Category category = categoryMapper.selectById(categoryId);
        if (category == null) return null;

        Category parent = category.getParentId() != null
                ? categoryMapper.selectById(category.getParentId()) : null;

        return ResourceItem.builder()
                .categoryId(category.getId())
                .categoryName(category.getName())
                .parentCategoryId(parent != null ? parent.getId() : null)
                .parentCategoryName(parent != null ? parent.getName() : null)
                .build();
    }

    /**
     * 将内部 Resource 实体映射为统一对外暴露的 ResourceItem，并补充分类层级名称。
     *
     * @param resources 资源实体列表
     * @return 填充完整展示名称的 ResourceItem 列表
     */
    private List<ResourceItem> toResourceItems(List<Resource> resources) {
        if (resources == null || resources.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, Category> categoryMap = categoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));

        return resources.stream().map(r -> {
            Category category = categoryMap.get(r.getCategoryId());
            Category parent = (category != null && category.getParentId() != null)
                    ? categoryMap.get(category.getParentId()) : null;

            return ResourceItem.builder()
                    .id(r.getId())
                    .title(r.getTitle())
                    .description(r.getDescription())
                    .categoryId(r.getCategoryId())
                    .categoryName(category != null ? category.getName() : null)
                    .parentCategoryId(parent != null ? parent.getId() : null)
                    .parentCategoryName(parent != null ? parent.getName() : null)
                    .coverUrl(r.getCoverUrl())
                    .heat(r.getHeat())
                    .createdAt(r.getCreatedAt())
                    .build();
        }).collect(Collectors.toList());
    }
}
