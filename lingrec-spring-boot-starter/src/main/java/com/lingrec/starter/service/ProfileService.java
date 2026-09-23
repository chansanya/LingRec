package com.lingrec.starter.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingrec.core.model.ResourceItem;
import com.lingrec.core.model.UserProfileDTO;
import com.lingrec.core.spi.ResourceItemProvider;
import com.lingrec.starter.entity.UserBehavior;
import com.lingrec.starter.entity.UserProfile;
import com.lingrec.starter.mapper.UserBehaviorMapper;
import com.lingrec.starter.mapper.UserProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用户兴趣画像计算服务，完全通过 ResourceItemProvider SPI 获取资源分类归属与分类元数据，不强耦合内置 resource/category 表。
 */
@RequiredArgsConstructor
public class ProfileService {
    private final UserProfileMapper profileMapper;
    private final UserBehaviorMapper behaviorMapper;
    private final ResourceItemProvider resourceItemProvider;

    /**
     * 查询用户小类兴趣画像并通过 SPI 补充分类名称与父分类名称。
     *
     * @param userId 用户主键
     * @return 包含分类名称与分值的用户画像列表
     */
    @Cacheable(value = "lingrec_profiles", key = "#userId", unless = "#result == null")
    public List<UserProfileDTO> getUserProfile(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }

        List<UserProfile> profiles = profileMapper.selectList(Wrappers.<UserProfile>lambdaQuery()
                .eq(UserProfile::getUserId, userId));
        if (profiles.isEmpty()) {
            return Collections.emptyList();
        }

        return profiles.stream().map(p -> {
            ResourceItem meta = (resourceItemProvider != null)
                    ? resourceItemProvider.getCategoryMetadata(p.getCategoryId()) : null;

            return UserProfileDTO.builder()
                    .categoryId(p.getCategoryId())
                    .categoryName(meta != null ? meta.getCategoryName() : null)
                    .parentCategoryName(meta != null ? meta.getParentCategoryName() : null)
                    .score(p.getScore())
                    .build();
        }).collect(Collectors.toList());
    }

    /**
     * 根据全部有效行为权重重新计算小类画像分数，通过 SPI 获取资源对应的分类 ID，并将最高分归一化为 100 分。
     *
     * @param userId 待重算画像的用户主键
     */
    @Transactional
    @CacheEvict(value = "lingrec_profiles", key = "#userId")
    public void recalculateProfile(Long userId) {
        if (userId == null) return;

        List<UserBehavior> behaviors = behaviorMapper.selectList(Wrappers.<UserBehavior>lambdaQuery()
                .eq(UserBehavior::getUserId, userId));
        if (behaviors.isEmpty()) {
            profileMapper.delete(Wrappers.<UserProfile>lambdaQuery()
                    .eq(UserProfile::getUserId, userId));
            return;
        }

        List<Long> resourceIds = behaviors.stream()
                .map(UserBehavior::getResourceId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, ResourceItem> resourceMap = (resourceIds.isEmpty() || resourceItemProvider == null)
                ? Collections.emptyMap()
                : resourceItemProvider.getByIds(resourceIds).stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toMap(ResourceItem::getId, Function.identity(), (a, b) -> a));

        Map<Long, Double> categoryScores = behaviors.stream()
                .filter(behavior -> resourceMap.containsKey(behavior.getResourceId())
                        && resourceMap.get(behavior.getResourceId()).getCategoryId() != null)
                .collect(Collectors.groupingBy(
                        behavior -> resourceMap.get(behavior.getResourceId()).getCategoryId(),
                        Collectors.summingDouble(behavior -> behavior.getAction().getWeight())
                ));

        double maxScore = categoryScores.values().stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(1.0);
        if (maxScore == 0) maxScore = 1.0;
        final double finalMaxScore = maxScore;

        profileMapper.delete(Wrappers.<UserProfile>lambdaQuery()
                .eq(UserProfile::getUserId, userId));

        LocalDateTime now = LocalDateTime.now();
        categoryScores.forEach((categoryId, score) -> profileMapper.insert(UserProfile.builder()
                .userId(userId)
                .categoryId(categoryId)
                .score((score / finalMaxScore) * 100.0)
                .updatedAt(now)
                .build()));
    }
}
