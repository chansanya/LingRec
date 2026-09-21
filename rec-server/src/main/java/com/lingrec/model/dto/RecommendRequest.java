package com.lingrec.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendRequest {
    private Long userId;
    private List<ProfileItem> userProfile;
    private List<ResourceItem> candidates;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProfileItem {
        private Long categoryId;
        private String categoryName;
        private Double score;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ResourceItem {
        private Long id;
        private String title;
        private Long categoryId;
        private String categoryName;
        private Integer heat;
        private String createdAt;
    }
}
