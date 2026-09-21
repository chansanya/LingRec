package com.lingrec.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingrec.mapper.CategoryMapper;
import com.lingrec.mapper.ResourceMapper;
import com.lingrec.model.entity.Category;
import com.lingrec.model.entity.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ResourceService {
    private final ResourceMapper resourceMapper;
    private final CategoryMapper categoryMapper;

    /**
     * 查询全部候选资源。
     *
     * @return 数据库中的全部资源实体
     */
    public List<Resource> getAllResources() {
        return resourceMapper.selectList(null);
    }

    /**
     * 查询直接归属于指定分类的资源。
     *
     * @param categoryId 资源小类主键
     * @return 直接关联该分类的资源列表
     */
    public List<Resource> getByCategory(Long categoryId) {
        return resourceMapper.selectList(Wrappers.<Resource>lambdaQuery()
                .eq(Resource::getCategoryId, categoryId));
    }

    /**
     * 批量查询归属于任一指定分类的资源。
     *
     * @param categoryIds 分类主键集合，可为空
     * @return 匹配的资源列表；分类集合为空时返回空列表
     */
    public List<Resource> getByCategoryIds(List<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return Collections.emptyList();
        }
        return resourceMapper.selectList(Wrappers.<Resource>lambdaQuery()
                .in(Resource::getCategoryId, categoryIds));
    }

    /**
     * 根据分类层级解析资源查询范围：空值查询全部，父分类包含所有子分类，子分类只查询自身。
     *
     * @param categoryId 可选分类主键
     * @return 分类范围内的资源列表
     * @throws ResponseStatusException 分类主键不存在时抛出 404 异常
     */
    public List<Resource> getByCategoryScope(Long categoryId) {
        if (categoryId == null) {
            return getAllResources();
        }

        Category category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "分类不存在");
        }

        if (category.getParentId() != null) {
            return getByCategory(categoryId);
        }

        List<Long> categoryIds = categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                        .eq(Category::getParentId, categoryId))
                .stream()
                .map(Category::getId)
                .collect(Collectors.toCollection(ArrayList::new));
        categoryIds.add(categoryId);
        return getByCategoryIds(categoryIds);
    }

    /**
     * 按主键查询资源。
     *
     * @param id 资源主键，可为空
     * @return 匹配的资源实体；主键为空或不存在时返回 null
     */
    public Resource getById(Long id) {
        return id == null ? null : resourceMapper.selectById(id);
    }

    /**
     * 按主键集合批量查询资源。
     *
     * @param ids 资源主键集合，可为空
     * @return 匹配的资源列表；主键集合为空时返回空列表
     */
    public List<Resource> getByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return resourceMapper.selectBatchIds(ids);
    }

    /**
     * 查询全局热度最高的 20 条资源。
     *
     * @return 按热度降序排列的资源列表，最多 20 条
     */
    public List<Resource> getHotResources() {
        return resourceMapper.selectList(Wrappers.<Resource>lambdaQuery()
                .orderByDesc(Resource::getHeat)
                .last("LIMIT 20"));
    }
}
