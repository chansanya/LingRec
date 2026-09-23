package com.lingrec.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 传递给 Python 推荐算法服务的排序打分请求数据结构。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 目标推荐用户主键 ID。
     */
    private Long userId;

    /**
     * 用户分类兴趣画像列表。
     */
    private List<ProfileItem> userProfile;

    /**
     * 待排序的召回候选资源列表。
     */
    private List<ResourceItem> candidates;

    /**
     * 算法打分所需的单项画像分值结构。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProfileItem implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 分类主键 ID。
         */
        private Long categoryId;

        /**
         * 分类名称。
         */
        private String categoryName;

        /**
         * 兴趣偏好得分（0.0 ~ 100.0）。
         */
        private Double score;
    }
}
