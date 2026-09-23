package com.lingrec.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 推荐资源统一数据实体传输模型，解耦业务宿主系统自有的商品、文章等不同数据表。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResourceItem implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 资源唯一主键 ID。
     */
    private Long id;

    /**
     * 资源标题。
     */
    private String title;

    /**
     * 资源简要说明或内容摘要。
     */
    private String description;

    /**
     * 所属细分子分类主键 ID。
     */
    private Long categoryId;

    /**
     * 所属细分子分类展示名称。
     */
    private String categoryName;

    /**
     * 上级父大类主键 ID，若本身为顶级分类则可为空。
     */
    private Long parentCategoryId;

    /**
     * 上级父大类展示名称。
     */
    private String parentCategoryName;

    /**
     * 封面或预览图链接地址。
     */
    private String coverUrl;

    /**
     * 资源热度分值，默认 0。
     */
    private Integer heat;

    /**
     * 资源发布或创建时间。
     */
    private LocalDateTime createdAt;
}
