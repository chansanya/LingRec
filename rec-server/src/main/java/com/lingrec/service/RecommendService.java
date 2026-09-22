package com.lingrec.service;

import com.lingrec.mapper.CategoryMapper;
import com.lingrec.mapper.UserMapper;
import com.lingrec.model.dto.RecommendPageResult;
import com.lingrec.model.dto.RecommendRequest;
import com.lingrec.model.dto.RecommendResponse;
import com.lingrec.model.entity.Category;
import com.lingrec.model.entity.Resource;
import com.lingrec.model.entity.UserProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendService {
    private static final String SESSION_KEY_PREFIX = "lingrec:rec:session:";

    private final ProfileService profileService;
    private final ResourceService resourceService;
    private final CategoryMapper categoryMapper;
    private final UserMapper userMapper;
    private final RestTemplate restTemplate;
    private final StringRedisTemplate redisTemplate;
    private final Map<String, LocalSession> localSessionCache = new ConcurrentHashMap<>();

    /**
     * 内存会话缓存实体，用于在 Redis 不可用时兜底保存推荐排序快照。
     */
    private static class LocalSession {
        private final List<Long> ids;
        private final long expireAt;

        /**
         * 构造带有效期的本地内存会话。
         *
         * @param ids 排序后的资源 ID 列表
         * @param ttlSeconds 有效时长（秒）
         */
        LocalSession(List<Long> ids, long ttlSeconds) {
            this.ids = ids;
            this.expireAt = System.currentTimeMillis() + ttlSeconds * 1000L;
        }

        /**
         * 判断当前本地会话是否已过期。
         *
         * @return true 表示已过期，false 表示仍然有效
         */
        boolean isExpired() {
            return System.currentTimeMillis() > expireAt;
        }
    }

    @Value("${lingrec.algorithm.url}")
    private String algorithmUrl;

    @Value("${lingrec.recommend.candidate-pool-limit:1000}")
    private int candidatePoolLimit;

    @Value("${lingrec.recommend.hot-candidate-limit:400}")
    private int hotCandidateLimit;

    @Value("${lingrec.recommend.fresh-candidate-limit:200}")
    private int freshCandidateLimit;

    @Value("${lingrec.recommend.interest-candidate-limit:300}")
    private int interestCandidateLimit;

    @Value("${lingrec.recommend.session-ttl-seconds:600}")
    private long sessionTtlSeconds;

    @Value("${lingrec.recommend.page-size:20}")
    private int defaultPageSize;

    /**
     * 为用户创建一次推荐会话：召回受限候选池、调用 Python 排序并将结果保存为 Redis 快照。
     *
     * @param userId 用户主键
     * @param categoryId 可选分类主键；为空查询全部，父分类覆盖其子分类，子分类仅查询自身
     * @return 推荐第一页及会话标识
     * @throws ResponseStatusException 用户或分类不存在时抛出 404 异常
     */
    public RecommendPageResult createRecommendationSession(Long userId, Long categoryId) {
        requireUser(userId);

        List<UserProfile> profile = profileService.getUserProfile(userId);
        List<Long> categoryIds = resourceService.resolveCategoryIds(categoryId);
        List<Resource> candidatePool = buildCandidatePool(categoryId, categoryIds, profile);

        String sessionId = UUID.randomUUID().toString().replace("-", "");
        List<Long> orderedIds = rankCandidatePool(userId, candidatePool, profile);
        saveSession(userId, sessionId, orderedIds);

        return buildPage(sessionId, orderedIds, 0, defaultPageSize);
    }

    /**
     * 按游标读取推荐会话快照的下一页，并保持快照内的稳定排序。
     *
     * @param userId 用户主键
     * @param sessionId 推荐会话标识
     * @param cursor 当前已加载条数，从 0 开始
     * @param size 本次加载条数
     * @return 推荐分页数据
     * @throws ResponseStatusException 用户不存在、会话不存在或过期、游标或条数非法时抛出异常
     */
    public RecommendPageResult getRecommendationPage(Long userId, String sessionId, int cursor, int size) {
        requireUser(userId);

        if (cursor < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cursor 不能小于 0");
        }
        int pageSize = size > 0 ? size : defaultPageSize;
        if (pageSize > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size 不能超过 100");
        }

        List<Long> orderedIds = loadSession(userId, sessionId);
        return buildPage(sessionId, orderedIds, cursor, pageSize);
    }

    /**
     * 召回有界候选池：合并热门、新鲜和兴趣候选并去重，避免加载全量资源。
     * 当用户指定了具体的分类筛选范围时，所有召回渠道（包含画像兴趣召回）均严格限定在该分类范围内，禁止任何跨分类内容污染。
     *
     * @param requestedCategoryId 用户请求的分类主键，为空表示全部分类（全局推荐模式）
     * @param categoryIds 目标分类主键集合（含子分类主键）
     * @param profile 用户兴趣画像
     * @return 严格限定在分类范围内的去重候选资源列表，最多 candidatePoolLimit 条
     */
    private List<Resource> buildCandidatePool(Long requestedCategoryId, List<Long> categoryIds, List<UserProfile> profile) {
        boolean isScoped = (requestedCategoryId != null);
        List<Resource> hot = resourceService.getHotCandidates(categoryIds, hotCandidateLimit);
        List<Resource> fresh = resourceService.getFreshCandidates(categoryIds, freshCandidateLimit);

        List<Long> targetInterestCategoryIds;
        if (isScoped) {
            // 分类过滤模式：仅在用户指定的分类集合与用户画像之间求交集，防止跨大类偏好强行渗透
            List<Long> profileInterestCatIds = resolveInterestCategoryIds(profile);
            targetInterestCategoryIds = profileInterestCatIds.stream()
                    .filter(categoryIds::contains)
                    .collect(Collectors.toList());
            if (targetInterestCategoryIds.isEmpty()) {
                targetInterestCategoryIds = categoryIds;
            }
        } else {
            // 全局推荐模式：按用户全网历史画像中最感兴趣的分类进行多类目混合召回
            targetInterestCategoryIds = resolveInterestCategoryIds(profile);
        }

        List<Resource> interest = resourceService.getInterestCandidates(targetInterestCategoryIds, interestCandidateLimit);

        LinkedHashSet<Long> seen = new LinkedHashSet<>();
        List<Resource> merged = new ArrayList<>();
        for (Resource resource : concat(hot, fresh, interest)) {
            if (resource == null || resource.getId() == null || !seen.add(resource.getId())) {
                continue;
            }
            // 兜底强约束：用户指定分类时，严禁任何非目标分类资源进入候选池
            if (isScoped && !categoryIds.contains(resource.getCategoryId())) {
                continue;
            }
            merged.add(resource);
            if (merged.size() >= candidatePoolLimit) {
                break;
            }
        }
        return merged;
    }

    /**
     * 调用 Python 对候选池排序，失败时按热度降级。
     *
     * @param userId 用户主键
     * @param candidatePool 有界候选池
     * @param profile 用户兴趣画像
     * @return 排序后的资源主键列表
     */
    private List<Long> rankCandidatePool(Long userId, List<Resource> candidatePool, List<UserProfile> profile) {
        if (candidatePool.isEmpty()) {
            return Collections.emptyList();
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

        List<RecommendRequest.ResourceItem> candidateItems = candidatePool.stream().map(resource -> {
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
                return response.getResourceIds().stream()
                        .distinct()
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("Algorithm service unavailable, using scoped hot-resource fallback: {}", e.getMessage());
        }

        return candidatePool.stream()
                .sorted(Comparator.comparingInt(
                        (Resource resource) -> resource.getHeat() == null ? 0 : resource.getHeat()).reversed())
                .map(Resource::getId)
                .collect(Collectors.toList());
    }

    /**
     * 从用户画像中提取兴趣分最高的五个分类主键。
     *
     * @param profile 用户兴趣画像
     * @return 兴趣分类主键列表
     */
    private List<Long> resolveInterestCategoryIds(List<UserProfile> profile) {
        if (profile == null || profile.isEmpty()) {
            return Collections.emptyList();
        }
        return profile.stream()
                .sorted(Comparator.comparingDouble(
                        (UserProfile item) -> item.getScore() == null ? 0 : item.getScore()).reversed())
                .limit(5)
                .map(UserProfile::getCategoryId)
                .collect(Collectors.toList());
    }

    /**
     * 将排序结果保存到会话存储中，优先写入 Redis，若发生异常则降级保存在内存缓存中。
     *
     * @param userId 用户主键
     * @param sessionId 会话标识
     * @param orderedIds 排序后的资源主键列表
     */
    private void saveSession(Long userId, String sessionId, List<Long> orderedIds) {
        String key = sessionKey(userId, sessionId);
        localSessionCache.put(key, new LocalSession(orderedIds, sessionTtlSeconds));
        try {
            if (redisTemplate != null) {
                String value = orderedIds.stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(","));
                redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(sessionTtlSeconds));
            }
        } catch (Exception e) {
            log.warn("Redis saveSession 异常，自动降级为本地内存会话缓存: {}", e.getMessage());
        }
    }

    /**
     * 从 Redis 或降级内存缓存中读取推荐会话排序快照。
     *
     * @param userId 用户主键
     * @param sessionId 会话标识
     * @return 排序后的资源主键列表
     * @throws ResponseStatusException 会话不存在或已过期时抛出 404 异常
     */
    private List<Long> loadSession(Long userId, String sessionId) {
        String key = sessionKey(userId, sessionId);
        String value = null;
        try {
            if (redisTemplate != null) {
                value = redisTemplate.opsForValue().get(key);
            }
        } catch (Exception e) {
            log.warn("Redis loadSession 异常，尝试从本地内存缓存读取: {}", e.getMessage());
        }

        if (value != null && !value.trim().isEmpty()) {
            String[] parts = value.split(",");
            List<Long> ids = new ArrayList<>(parts.length);
            for (String part : parts) {
                if (part != null && !part.trim().isEmpty()) {
                    ids.add(Long.valueOf(part.trim()));
                }
            }
            return ids;
        }

        LocalSession localSession = localSessionCache.get(key);
        if (localSession != null && !localSession.isExpired()) {
            return localSession.ids;
        }

        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "推荐会话不存在或已过期");
    }

    /**
     * 根据排序快照构建指定游标的一页数据，并保持快照顺序。
     *
     * @param sessionId 会话标识
     * @param orderedIds 排序快照
     * @param cursor 起始游标
     * @param size 页大小
     * @return 分页推荐结果
     */
    private RecommendPageResult buildPage(String sessionId, List<Long> orderedIds, int cursor, int size) {
        int total = orderedIds.size();
        int from = Math.min(cursor, total);
        int to = Math.min(cursor + size, total);
        List<Long> pageIds = orderedIds.subList(from, to);

        Map<Long, Resource> resourceMap = pageIds.isEmpty()
                ? Collections.emptyMap()
                : resourceService.getByIds(pageIds).stream()
                        .collect(Collectors.toMap(Resource::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));

        List<Resource> items = pageIds.stream()
                .map(resourceMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return RecommendPageResult.builder()
                .sessionId(sessionId)
                .items(items)
                .nextCursor(to)
                .hasMore(to < total)
                .total(total)
                .build();
    }

    /**
     * 校验用户主键是否有效且对应记录存在。
     *
     * @param userId 用户主键
     */
    private void requireUser(Long userId) {
        if (userId == null || userMapper.selectById(userId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
    }

    /**
     * 拼接多个资源列表为单个迭代顺序。
     *
     * @param lists 多个资源列表
     * @return 合并后的资源列表
     */
    @SafeVarargs
    private static List<Resource> concat(List<Resource>... lists) {
        List<Resource> result = new ArrayList<>();
        for (List<Resource> list : lists) {
            if (list != null) {
                result.addAll(list);
            }
        }
        return result;
    }

    /**
     * 构建会话在 Redis 中使用的键。
     *
     * @param userId 用户主键
     * @param sessionId 会话标识
     * @return Redis 键
     */
    private String sessionKey(Long userId, String sessionId) {
        return SESSION_KEY_PREFIX + userId + ":" + sessionId;
    }
}
