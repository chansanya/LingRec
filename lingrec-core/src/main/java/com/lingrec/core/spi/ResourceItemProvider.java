package com.lingrec.core.spi;

import com.lingrec.core.model.ResourceItem;

import java.util.List;

/**
 * 业务资源数据源适配器 SPI。
 * 允许外部 Spring Boot 接入工程在不修改已有商品、文章、视频库表结构的前提下，
 * 完整接管推荐引擎的分类解析、候选召回、详情组装、热度变更以及画像分类元数据回填。
 */
public interface ResourceItemProvider {
    /**
     * 解析给定分类标识所涵盖的所有有效子分类主键集合。
     * 若入参为 null，表示全部分类范围；若入参为父分类，返回其全部子分类；若入参为子分类，返回仅含自身的集合。
     *
     * @param categoryId 可选分类主键，可为空
     * @return 该作用域下的全部叶子分类主键列表，不可为 null
     */
    List<Long> resolveCategoryIds(Long categoryId);

    /**
     * 多路召回通道 1：按分类作用域和条数配额拉取热门候选资源。
     *
     * @param categoryIds 目标分类主键集合，为空表示全部分类
     * @param limit 最大返回条数限制
     * @return 作用域内按热度降序排列的候选资源列表
     */
    List<ResourceItem> getHotCandidates(List<Long> categoryIds, int limit);

    /**
     * 多路召回通道 2：按分类作用域和条数配额拉取最新鲜候选资源。
     *
     * @param categoryIds 目标分类主键集合，为空表示全部分类
     * @param limit 最大返回条数限制
     * @return 作用域内按创建时间降序排列的候选资源列表
     */
    List<ResourceItem> getFreshCandidates(List<Long> categoryIds, int limit);

    /**
     * 多路召回通道 3：按画像兴趣分类和条数配额拉取个性化候选资源。
     *
     * @param interestCategoryIds 经过分类作用域交集计算后的目标兴趣分类主键列表
     * @param limit 最大返回条数限制
     * @return 指定分类下按热度或相关性排序的候选资源列表
     */
    List<ResourceItem> getInterestCandidates(List<Long> interestCategoryIds, int limit);

    /**
     * 详情回填通道：根据排序后的资源主键序列批量查询完整资源展示详情。
     *
     * @param resourceIds 资源主键集合，不可为 null
     * @return 对应的资源模型列表，需包含 id 与 categoryId 等核心属性
     */
    List<ResourceItem> getByIds(List<Long> resourceIds);

    /**
     * 热度写回通道：当发生浏览、点赞、收藏或二次点击取消时，同步增减业务表中的资源热度。
     *
     * @param resourceId 资源主键 ID
     * @param delta 热度变化量（正数为累加，负数为取消扣减）
     * @return 变更后的最新热度值（最小保底为 0）
     */
    int updateHeat(Long resourceId, int delta);

    /**
     * 分类元数据回填通道：根据分类 ID 获取分类名称及父分类名称（用于画像统计展示）。
     *
     * @param categoryId 分类主键 ID
     * @return 填充了 categoryId、categoryName、parentCategoryName 的 ResourceItem 载体；不存在时返回 null
     */
    ResourceItem getCategoryMetadata(Long categoryId);
}
