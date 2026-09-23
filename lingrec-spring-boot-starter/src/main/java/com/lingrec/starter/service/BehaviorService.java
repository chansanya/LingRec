package com.lingrec.starter.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingrec.core.enums.ActionType;
import com.lingrec.core.model.BehaviorRequest;
import com.lingrec.core.model.BehaviorResult;
import com.lingrec.core.spi.ResourceItemProvider;
import com.lingrec.starter.entity.UserBehavior;
import com.lingrec.starter.mapper.UserBehaviorMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 用户行为状态机核心服务，负责行为记录、二次点击取消、SPI 热度动态回写与交互主键聚合。
 */
@RequiredArgsConstructor
public class BehaviorService {
    private static final int MAX_BEHAVIOR_LIMIT = 100;

    private final UserBehaviorMapper behaviorMapper;
    private final ResourceItemProvider resourceItemProvider;
    private final ProfileService profileService;

    /**
     * 记录用户行为，点赞与收藏在二次点击时自动取消，通过 SPI 更新业务资源热度并立即重算用户画像。
     *
     * @param request 行为请求对象，包含用户 ID、资源 ID、行为类型及可选显式 cancel 标识
     * @return 包含操作状态、当前激活态及最新热度分值的结构化结果
     */
    @Transactional
    public BehaviorResult recordBehavior(BehaviorRequest request) {
        return persistBehavior(request, true, true);
    }

    /**
     * 记录生成器或批量导入行为并更新资源热度，延迟画像重算且不触发二次点击取消。
     *
     * @param request 行为请求对象
     * @return 包含操作状态与热度分值的结构化结果
     */
    @Transactional
    public BehaviorResult recordBehaviorWithoutProfileRefresh(BehaviorRequest request) {
        return persistBehavior(request, false, false);
    }

    /**
     * 显式取消指定用户的点赞或收藏行为，扣减资源热度并立即重新计算用户画像。
     *
     * @param userId 用户主键
     * @param resourceId 资源主键
     * @param action 行为类型（仅支持 LIKE 或 FAVORITE 取消）
     * @return 包含取消状态与扣减后热度的结构化结果
     */
    @Transactional
    public BehaviorResult cancelBehavior(Long userId, Long resourceId, ActionType action) {
        if (action == null || action == ActionType.VIEW) {
            throw new IllegalArgumentException("仅支持取消 LIKE 或 FAVORITE 行为");
        }
        BehaviorRequest request = BehaviorRequest.builder()
                .userId(userId)
                .resourceId(resourceId)
                .action(action.name())
                .cancel(true)
                .build();
        return persistBehavior(request, true, true);
    }

    /**
     * 查询指定用户在特定行为类型下产生过交互的全部去重资源主键列表（用于客户端 O(1) 状态判定）。
     *
     * @param userId 用户主键
     * @param action 行为类型
     * @return 该用户产生过指定行为的资源主键去重列表
     */
    public List<Long> getInteractedResourceIds(Long userId, ActionType action) {
        if (userId == null || action == null) {
            return Collections.emptyList();
        }
        List<UserBehavior> behaviors = behaviorMapper.selectList(Wrappers.<UserBehavior>lambdaQuery()
                .select(UserBehavior::getResourceId)
                .eq(UserBehavior::getUserId, userId)
                .eq(UserBehavior::getAction, action));
        return behaviors.stream()
                .map(UserBehavior::getResourceId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 查询用户最近行为明细。
     *
     * @param userId 用户主键
     * @param action 可选行为过滤条件
     * @param limit 返回条数上限
     * @return 按时间倒序的行为实体列表
     */
    public List<UserBehavior> getUserBehaviors(Long userId, ActionType action, int limit) {
        if (userId == null) {
            return Collections.emptyList();
        }
        int validLimit = Math.min(Math.max(limit, 1), MAX_BEHAVIOR_LIMIT);
        return behaviorMapper.selectList(Wrappers.<UserBehavior>lambdaQuery()
                .eq(UserBehavior::getUserId, userId)
                .eq(action != null, UserBehavior::getAction, action)
                .orderByDesc(UserBehavior::getCreatedAt)
                .last("LIMIT " + validLimit));
    }

    /**
     * 统计用户各行为类型的累计发生次数。
     *
     * @param userId 用户主键
     * @return 以 VIEW、LIKE、FAVORITE 为键的行为计数映射
     */
    public Map<String, Long> getBehaviorStats(Long userId) {
        Map<String, Long> stats = new HashMap<>();
        if (userId == null) {
            return stats;
        }
        for (ActionType action : ActionType.values()) {
            Long count = behaviorMapper.selectCount(Wrappers.<UserBehavior>lambdaQuery()
                    .eq(UserBehavior::getUserId, userId)
                    .eq(UserBehavior::getAction, action));
            stats.put(action.name(), count == null ? 0L : count);
        }
        return stats;
    }

    /**
     * 执行行为校验、持久化写入或物理删除取消，通过 SPI 回调更新业务资源热度并按需触发画像重算。
     *
     * @param request 行为请求
     * @param refreshProfile 是否立即刷新画像
     * @param allowToggle 是否允许在重复点击时自动翻转取消
     * @return 行为操作反馈结果
     */
    private BehaviorResult persistBehavior(BehaviorRequest request, boolean refreshProfile, boolean allowToggle) {
        if (request == null || request.getUserId() == null || request.getResourceId() == null
                || request.getAction() == null || request.getAction().trim().isEmpty()) {
            throw new IllegalArgumentException("用户、资源和行为类型不能为空");
        }

        ActionType action;
        try {
            action = ActionType.valueOf(request.getAction().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("不支持的行为类型: " + request.getAction());
        }

        List<UserBehavior> existing = (action == ActionType.VIEW)
                ? Collections.emptyList()
                : behaviorMapper.selectList(Wrappers.<UserBehavior>lambdaQuery()
                        .eq(UserBehavior::getUserId, request.getUserId())
                        .eq(UserBehavior::getResourceId, request.getResourceId())
                        .eq(UserBehavior::getAction, action));

        boolean shouldCancel;
        if (request.getCancel() != null) {
            if (action == ActionType.VIEW && Boolean.TRUE.equals(request.getCancel())) {
                throw new IllegalArgumentException("不支持取消浏览行为");
            }
            shouldCancel = Boolean.TRUE.equals(request.getCancel());
        } else {
            shouldCancel = allowToggle && (action == ActionType.LIKE || action == ActionType.FAVORITE) && !existing.isEmpty();
        }

        int currentHeat = 0;
        if (shouldCancel) {
            if (!existing.isEmpty()) {
                int deletedCount = behaviorMapper.delete(Wrappers.<UserBehavior>lambdaQuery()
                        .eq(UserBehavior::getUserId, request.getUserId())
                        .eq(UserBehavior::getResourceId, request.getResourceId())
                        .eq(UserBehavior::getAction, action));
                int decrease = (int) (action.getWeight() * (deletedCount > 0 ? deletedCount : 1));
                if (resourceItemProvider != null) {
                    currentHeat = resourceItemProvider.updateHeat(request.getResourceId(), -decrease);
                }

                if (refreshProfile && profileService != null) {
                    profileService.recalculateProfile(request.getUserId());
                }
            } else if (resourceItemProvider != null) {
                currentHeat = resourceItemProvider.updateHeat(request.getResourceId(), 0);
            }

            return BehaviorResult.builder()
                    .id(null)
                    .userId(request.getUserId())
                    .resourceId(request.getResourceId())
                    .action(action.name())
                    .status("CANCELLED")
                    .active(false)
                    .heat(currentHeat)
                    .createdAt(null)
                    .build();
        }

        if ((action == ActionType.LIKE || action == ActionType.FAVORITE) && !existing.isEmpty()) {
            UserBehavior first = existing.get(0);
            if (resourceItemProvider != null) {
                currentHeat = resourceItemProvider.updateHeat(request.getResourceId(), 0);
            }
            return BehaviorResult.builder()
                    .id(first.getId())
                    .userId(first.getUserId())
                    .resourceId(first.getResourceId())
                    .action(first.getAction().name())
                    .status("RECORDED")
                    .active(true)
                    .heat(currentHeat)
                    .createdAt(first.getCreatedAt())
                    .build();
        }

        UserBehavior behavior = UserBehavior.builder()
                .userId(request.getUserId())
                .resourceId(request.getResourceId())
                .action(action)
                .createdAt(LocalDateTime.now())
                .build();
        behaviorMapper.insert(behavior);

        if (resourceItemProvider != null) {
            currentHeat = resourceItemProvider.updateHeat(request.getResourceId(), (int) action.getWeight());
        }

        if (refreshProfile && profileService != null) {
            profileService.recalculateProfile(request.getUserId());
        }

        return BehaviorResult.builder()
                .id(behavior.getId())
                .userId(behavior.getUserId())
                .resourceId(behavior.getResourceId())
                .action(behavior.getAction().name())
                .status("RECORDED")
                .active(true)
                .heat(currentHeat)
                .createdAt(behavior.getCreatedAt())
                .build();
    }
}
