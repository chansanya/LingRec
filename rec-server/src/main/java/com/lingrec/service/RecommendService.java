package com.lingrec.service;

import com.lingrec.mapper.CategoryMapper;
import com.lingrec.mapper.UserMapper;
import com.lingrec.model.dto.RecommendRequest;
import com.lingrec.model.dto.RecommendResponse;
import com.lingrec.model.entity.Category;
import com.lingrec.model.entity.Resource;
import com.lingrec.model.entity.UserProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendService {
    private final ProfileService profileService;
    private final ResourceService resourceService;
    private final CategoryMapper categoryMapper;
    private final UserMapper userMapper;
    private final RestTemplate restTemplate;

    @Value("${lingrec.algorithm.url}")
    private String algorithmUrl;

    /**
     * 构建用户画像和候选资源请求 Python 算法服务；算法不可用时在当前分类范围内按热度降级。
     *
     * @param userId 用户主键
     * @param categoryId 可选分类主键；为空查询全部，父分类覆盖其子分类，子分类仅查询自身
     * @return 按算法或降级策略排序的推荐资源列表，最多 20 条
     * @throws ResponseStatusException 用户或分类不存在时抛出 404 异常
     */
    public List<Resource> getRecommendations(Long userId, Long categoryId) {
        if (userId == null || userMapper.selectById(userId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }

        List<UserProfile> profile = profileService.getUserProfile(userId);
        List<Resource> candidates = resourceService.getByCategoryScope(categoryId);
        if (candidates.isEmpty()) {
            return candidates;
        }

        Map<Long, Category> categoryMap = categoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));

        List<RecommendRequest.ProfileItem> profileItems = profile.stream().map(item -> {
            Category category = categoryMap.get(item.getCategoryId());
            return RecommendRequest.ProfileItem.builder()
                    .categoryId(item.getCategoryId())
                    .categoryName(category != null ? category.getName() : "Unknown")
                    .score(item.getScore())
                    .build();
        }).collect(Collectors.toList());

        List<RecommendRequest.ResourceItem> candidateItems = candidates.stream().map(resource -> {
            Category category = categoryMap.get(resource.getCategoryId());
            return RecommendRequest.ResourceItem.builder()
                    .id(resource.getId())
                    .title(resource.getTitle())
                    .categoryId(resource.getCategoryId())
                    .categoryName(category != null ? category.getName() : "Unknown")
                    .heat(resource.getHeat())
                    .createdAt(resource.getCreatedAt() != null ? resource.getCreatedAt().toString() : null)
                    .build();
        }).collect(Collectors.toList());

        RecommendRequest request = RecommendRequest.builder()
                .userId(userId)
                .userProfile(profileItems)
                .candidates(candidateItems)
                .build();

        try {
            RecommendResponse response = restTemplate.postForObject(
                    algorithmUrl + "/recommend", request, RecommendResponse.class);

            if (response != null && response.getResourceIds() != null) {
                Map<Long, Resource> resourceMap = candidates.stream()
                        .collect(Collectors.toMap(Resource::getId, Function.identity()));
                return response.getResourceIds().stream()
                        .map(resourceMap::get)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("Algorithm service unavailable, using scoped hot-resource fallback: {}", e.getMessage());
        }
        return fallbackByHeat(candidates);
    }

    /**
     * 在已经限定分类范围的候选集中按热度生成降级推荐。
     *
     * @param candidates 当前推荐请求的候选资源
     * @return 按热度降序排列的候选资源，最多 20 条
     */
    private List<Resource> fallbackByHeat(List<Resource> candidates) {
        return candidates.stream()
                .sorted(Comparator.comparingInt(
                        (Resource resource) -> resource.getHeat() == null ? 0 : resource.getHeat()).reversed())
                .limit(20)
                .collect(Collectors.toList());
    }
}
