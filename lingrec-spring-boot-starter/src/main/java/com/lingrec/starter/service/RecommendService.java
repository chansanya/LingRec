package com.lingrec.starter.service;

import com.lingrec.core.client.AlgorithmClient;
import com.lingrec.core.model.RecommendPageResult;
import com.lingrec.core.model.ResourceItem;
import com.lingrec.core.model.UserProfileDTO;
import com.lingrec.core.spi.ResourceItemProvider;
import com.lingrec.starter.config.LingRecProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

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

/**
 * 推荐引擎核心服务：负责分类作用域隔离召回、Python 算法重排协同以及 L1/L2 双级快照游标分页。
 */
@Slf4j
@RequiredArgsConstructor
public class RecommendService {
    private static final String SESSION_KEY_PREFIX = "lingrec:rec:session:";

    private final ProfileService profileService;
    private final ResourceItemProvider resourceItemProvider;
    private final AlgorithmClient algorithmClient;
    private final LingRecProperties properties;
    private final StringRedisTemplate redisTemplate;

    private final Map<String, LocalSession> localSessionCache = new ConcurrentHashMap<>();

    /**
     * 本地内存会话结构，用于 Redis 不可用时保证推荐服务平滑可用。
     */
    private static class LocalSession {
        private final List<Long> ids;
        private final long expireAt;

        LocalSession(List<Long> ids, long ttlSeconds) {
            this.ids = ids;
            this.expireAt = System.currentTimeMillis() + ttlSeconds * 1000L;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expireAt;
        }
    }

    /**
     * 为用户创建一次推荐会话：作用域受限候选池召回、委派 Python 打分并写入会话快照。
     *
     * @param userId 用户主键
     * @param categoryId 可选目标分类主键（为空表示全部分类）
     * @return 包含第一页推荐数据与会话 ID 的结果对象
     */
    public RecommendPageResult createRecommendationSession(Long userId, Long categoryId) {
        if (userId == null) {
            throw new IllegalArgumentException("用户 ID 不能为空");
        }

        List<UserProfileDTO> profile = profileService.getUserProfile(userId);
        List<Long> categoryIds = resourceItemProvider.resolveCategoryIds(categoryId);
        List<ResourceItem> candidatePool = buildCandidatePool(categoryId, categoryIds, profile);

        String sessionId = UUID.randomUUID().toString().replace("-", "");
        List<Long> orderedIds = rankCandidatePool(userId, candidatePool, profile);
        saveSession(userId, sessionId, orderedIds);

        int pageSize = properties.getRecommend().getPageSize();
        return buildPage(sessionId, orderedIds, 0, pageSize);
    }

    /**
     * 按游标切片读取会话快照中的下一批推荐资源，保证多批次滚动时的序列稳定性。
     *
     * @param userId 用户主键
     * @param sessionId 会话标识
     * @param cursor 起始游标偏移量
     * @param size 请求读取的批次条数
     * @return 游标切片分页结果
     */
    public RecommendPageResult getRecommendationPage(Long userId, String sessionId, int cursor, int size) {
        if (userId == null || sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("用户 ID 与会话 ID 不能为空");
        }
        if (cursor < 0) {
            throw new IllegalArgumentException("游标 cursor 不能小于 0");
        }

        int pageSize = size > 0 ? size : properties.getRecommend().getPageSize();
        List<Long> orderedIds = loadSession(userId, sessionId);
        return buildPage(sessionId, orderedIds, cursor, pageSize);
    }

    /**
     * 召回有界候选池：支持分类作用域隔离（Scoped Mode），彻底杜绝跨大类偏好污染。
     *
     * @param requestedCategoryId 请求的分类 ID
     * @param categoryIds 解析后的有效分类集合
     * @param profile 用户画像列表
     * @return 严格受限的去重候选列表
     */
    private List<ResourceItem> buildCandidatePool(Long requestedCategoryId, List<Long> categoryIds, List<UserProfileDTO> profile) {
        boolean isScoped = (requestedCategoryId != null);
        int hotLimit = properties.getRecommend().getHotCandidateLimit();
        int freshLimit = properties.getRecommend().getFreshCandidateLimit();
        int interestLimit = properties.getRecommend().getInterestCandidateLimit();
        int poolLimit = properties.getRecommend().getCandidatePoolLimit();

        List<ResourceItem> hot = resourceItemProvider.getHotCandidates(categoryIds, hotLimit);
        List<ResourceItem> fresh = resourceItemProvider.getFreshCandidates(categoryIds, freshLimit);

        List<Long> targetInterestCategoryIds;
        if (isScoped) {
            List<Long> profileInterestCatIds = resolveInterestCategoryIds(profile);
            targetInterestCategoryIds = profileInterestCatIds.stream()
                    .filter(categoryIds::contains)
                    .collect(Collectors.toList());
            if (targetInterestCategoryIds.isEmpty()) {
                targetInterestCategoryIds = categoryIds;
            }
        } else {
            targetInterestCategoryIds = resolveInterestCategoryIds(profile);
        }

        List<ResourceItem> interest = resourceItemProvider.getInterestCandidates(targetInterestCategoryIds, interestLimit);

        LinkedHashSet<Long> seen = new LinkedHashSet<>();
        List<ResourceItem> merged = new ArrayList<>();
        for (ResourceItem item : concat(hot, fresh, interest)) {
            if (item == null || item.getId() == null || !seen.add(item.getId())) {
                continue;
            }
            if (isScoped && item.getCategoryId() != null && !categoryIds.contains(item.getCategoryId())) {
                continue;
            }
            merged.add(item);
            if (merged.size() >= poolLimit) {
                break;
            }
        }
        return merged;
    }

    /**
     * 调用 Python 算法服务打分重排，在算法不可用时平滑降级按热度排序。
     *
     * @param userId 用户 ID
     * @param candidatePool 候选资源集
     * @param profile 用户画像
     * @return 排序后的资源 ID 列表
     */
    private List<Long> rankCandidatePool(Long userId, List<ResourceItem> candidatePool, List<UserProfileDTO> profile) {
        if (candidatePool == null || candidatePool.isEmpty()) {
            return Collections.emptyList();
        }

        if (algorithmClient != null) {
            List<Long> ranked = algorithmClient.rank(userId, profile, candidatePool);
            if (ranked != null && !ranked.isEmpty()) {
                return ranked;
            }
        }

        return candidatePool.stream()
                .sorted(Comparator.comparingInt((ResourceItem r) -> r.getHeat() == null ? 0 : r.getHeat()).reversed())
                .map(ResourceItem::getId)
                .collect(Collectors.toList());
    }

    /**
     * 从画像中提取得分最高的五个分类 ID。
     *
     * @param profile 用户画像
     * @return 兴趣最高的分类 ID 列表
     */
    private List<Long> resolveInterestCategoryIds(List<UserProfileDTO> profile) {
        if (profile == null || profile.isEmpty()) {
            return Collections.emptyList();
        }
        return profile.stream()
                .sorted(Comparator.comparingDouble((UserProfileDTO item) -> item.getScore() == null ? 0 : item.getScore()).reversed())
                .limit(5)
                .map(UserProfileDTO::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 将排序结果存入双级会话存储（L1 本地内存 + L2 Redis）。
     *
     * @param userId 用户 ID
     * @param sessionId 会话 ID
     * @param orderedIds 排序主键序列
     */
    private void saveSession(Long userId, String sessionId, List<Long> orderedIds) {
        String key = sessionKey(userId, sessionId);
        long ttlSeconds = properties.getRecommend().getSessionTtlSeconds();
        localSessionCache.put(key, new LocalSession(orderedIds, ttlSeconds));

        if (properties.getCache().isPreferRedis() && redisTemplate != null) {
            try {
                String value = orderedIds.stream().map(String::valueOf).collect(Collectors.joining(","));
                redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(ttlSeconds));
            } catch (Exception e) {
                log.warn("Redis saveSession 异常，已自动降级使用本地内存会话缓存: {}", e.getMessage());
            }
        }
    }

    /**
     * 从双级缓存中读取排序会话快照。
     *
     * @param userId 用户 ID
     * @param sessionId 会话 ID
     * @return 排序主键序列
     */
    private List<Long> loadSession(Long userId, String sessionId) {
        String key = sessionKey(userId, sessionId);
        String value = null;

        if (properties.getCache().isPreferRedis() && redisTemplate != null) {
            try {
                value = redisTemplate.opsForValue().get(key);
            } catch (Exception e) {
                log.warn("Redis loadSession 异常，尝试从本地内存读取: {}", e.getMessage());
            }
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

        throw new IllegalStateException("推荐会话已过期或不存在，请刷新页面重新获取");
    }

    /**
     * 根据排序快照与游标切片组装一页展示数据。
     *
     * @param sessionId 会话标识
     * @param orderedIds 全量排序 ID
     * @param cursor 起始游标
     * @param size 页大小
     * @return 组装完成的分页结果
     */
    private RecommendPageResult buildPage(String sessionId, List<Long> orderedIds, int cursor, int size) {
        int total = orderedIds.size();
        int from = Math.min(cursor, total);
        int to = Math.min(cursor + size, total);
        List<Long> pageIds = orderedIds.subList(from, to);

        List<ResourceItem> details = pageIds.isEmpty() ? Collections.emptyList() : resourceItemProvider.getByIds(pageIds);
        Map<Long, ResourceItem> map = details.stream().collect(Collectors.toMap(ResourceItem::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));

        List<ResourceItem> items = pageIds.stream()
                .map(map::get)
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
     * 拼接多个列表。
     *
     * @param lists 多个资源列表
     * @return 合并列表
     */
    @SafeVarargs
    private static List<ResourceItem> concat(List<ResourceItem>... lists) {
        List<ResourceItem> result = new ArrayList<>();
        for (List<ResourceItem> list : lists) {
            if (list != null) {
                result.addAll(list);
            }
        }
        return result;
    }

    private String sessionKey(Long userId, String sessionId) {
        return SESSION_KEY_PREFIX + userId + ":" + sessionId;
    }
}
