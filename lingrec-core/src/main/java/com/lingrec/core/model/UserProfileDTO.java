package com.lingrec.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户小类兴趣画像分值数据传输对象。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfileDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 细分子分类主键 ID。
     */
    private Long categoryId;

    /**
     * 细分子分类展示名称。
     */
    private String categoryName;

    /**
     * 上级父大类展示名称。
     */
    private String parentCategoryName;

    /**
     * 归一化后的兴趣偏好分数（最高分类归一化为 100.0，范围 0.0 到 100.0）。
     */
    private Double score;
}
