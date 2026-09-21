package com.lingrec.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingrec.mapper.ResourceMapper;
import com.lingrec.mapper.UserBehaviorMapper;
import com.lingrec.mapper.UserProfileMapper;
import com.lingrec.model.entity.Resource;
import com.lingrec.model.entity.UserBehavior;
import com.lingrec.model.entity.UserProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProfileService {
    private final UserProfileMapper profileMapper;
    private final UserBehaviorMapper behaviorMapper;
    private final ResourceMapper resourceMapper;

    /**
     * 查询用户按资源小类聚合的兴趣画像，并缓存查询结果。
     *
     * @param userId 用户主键
     * @return 用户画像列表；无行为画像时返回空列表
     */
    @Cacheable(value = "profiles", key = "#userId")
    public List<UserProfile> getUserProfile(Long userId) {
        return profileMapper.selectList(Wrappers.<UserProfile>lambdaQuery()
                .eq(UserProfile::getUserId, userId));
    }

    /**
     * 根据用户全部行为权重重新计算小类兴趣分，将最高分类归一化为 100，并清理对应缓存。
     *
     * @param userId 需要重新计算画像的用户主键
     */
    @Transactional
    @CacheEvict(value = "profiles", key = "#userId")
    public void recalculateProfile(Long userId) {
        List<UserBehavior> behaviors = behaviorMapper.selectList(Wrappers.<UserBehavior>lambdaQuery()
                .eq(UserBehavior::getUserId, userId));
        if (behaviors.isEmpty()) {
            profileMapper.delete(Wrappers.<UserProfile>lambdaQuery()
                    .eq(UserProfile::getUserId, userId));
            return;
        }

        List<Long> resourceIds = behaviors.stream()
                .map(UserBehavior::getResourceId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Resource> resourceMap = resourceIds.isEmpty()
                ? Collections.emptyMap()
                : resourceMapper.selectBatchIds(resourceIds).stream()
                        .collect(Collectors.toMap(Resource::getId, Function.identity()));

        Map<Long, Double> categoryScores = behaviors.stream()
                .filter(behavior -> resourceMap.containsKey(behavior.getResourceId()))
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
