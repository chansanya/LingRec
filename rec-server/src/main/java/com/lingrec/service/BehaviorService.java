package com.lingrec.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lingrec.mapper.ResourceMapper;
import com.lingrec.mapper.UserBehaviorMapper;
import com.lingrec.mapper.UserMapper;
import com.lingrec.model.dto.BehaviorRequest;
import com.lingrec.model.entity.Resource;
import com.lingrec.model.entity.UserBehavior;
import com.lingrec.model.enums.ActionType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BehaviorService {
    private static final int MAX_BEHAVIOR_LIMIT = 100;

    private final UserBehaviorMapper behaviorMapper;
    private final ResourceMapper resourceMapper;
    private final UserMapper userMapper;
    private final ProfileService profileService;

    /**
     * 记录用户行为，点赞与收藏在二次点击时自动取消，更新资源热度并立即重新计算用户画像。
     *
     * @param request 行为请求对象，必须包含有效的用户 ID、资源 ID 和行为类型，支持可选 cancel 显式指定状态
     * @return 包含行为执行状态（RECORDED/CANCELLED）、当前激活态、更新后热度及主键等信息的结构化响应
     * @throws ResponseStatusException 请求参数非法、用户不存在或资源不存在时抛出
     */
    @Transactional
    public Map<String, Object> recordBehavior(BehaviorRequest request) {
        return persistBehavior(request, true, true);
    }

    /**
     * 记录生成器构造的用户行为并更新资源热度，延迟画像刷新且不触发二次点击取消，避免生成阶段状态翻转。
     *
     * @param request 行为请求对象，必须包含有效的用户 ID、资源 ID 和行为类型
     * @return 包含行为执行状态、当前激活态、更新后热度等信息的结构化响应
     * @throws ResponseStatusException 请求参数非法、用户不存在或资源不存在时抛出
     */
    @Transactional
    public Map<String, Object> recordBehaviorWithoutProfileRefresh(BehaviorRequest request) {
        return persistBehavior(request, false, false);
    }

    /**
     * 显式取消指定用户的点赞或收藏行为，扣减对应资源热度并立即重新计算用户画像。
     *
     * @param userId 用户主键，不能为空且对应用户必须存在
     * @param resourceId 资源主键，不能为空且对应资源必须存在
     * @param action 待取消的行为类型，仅支持 LIKE 或 FAVORITE
     * @return 包含取消状态、当前激活态及更新后热度的结构化响应
     * @throws ResponseStatusException 用户不存在、资源不存在或行为类型不支持取消时抛出
     */
    @Transactional
    public Map<String, Object> cancelBehavior(Long userId, Long resourceId, ActionType action) {
        if (action == null || action == ActionType.VIEW) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持取消 LIKE 或 FAVORITE 行为");
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
     * 查询指定用户在特定行为类型下产生过交互的全部去重资源主键列表。
     *
     * @param userId 用户主键，必须有效且存在对应用户
     * @param action 待查询的行为类型，为空时抛出 400 异常
     * @return 该用户产生过指定行为的资源主键去重列表；无记录时返回空列表
     * @throws ResponseStatusException 用户主键非法、用户不存在或行为类型为空时抛出
     */
    public List<Long> getInteractedResourceIds(Long userId, ActionType action) {
        requireUser(userId);
        if (action == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "行为类型不能为空");
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
     * 执行行为校验、持久化或取消逻辑、资源热度变更，并按开关决定是否刷新画像。
     * 针对 LIKE 和 FAVORITE，在 allowToggle 为 true 时若已存在记录则判定为取消：
     * 删除已有行为记录，扣减对应资源热度（保底为 0），并联动重算画像。
     * 对于 VIEW 或尚未记录的 LIKE/FAVORITE，则新增行为记录并累加资源热度。
     *
     * @param request 待处理的行为请求，包含用户 ID、资源 ID 和行为类型字符串
     * @param refreshProfile true 表示行为变更后立即重算画像，false 表示不触发重算
     * @param allowToggle true 表示对于 LIKE/FAVORITE 行为允许二次点击取消，false 表示强制新增记录
     * @return 包含操作状态（RECORDED 表示已记录，CANCELLED 表示已取消）、行为类型、资源 ID 及热度的映射结构
     * @throws ResponseStatusException 请求参数非法、用户不存在或资源不存在时抛出 400 或 404 异常
     */
    private Map<String, Object> persistBehavior(BehaviorRequest request, boolean refreshProfile, boolean allowToggle) {
        if (request == null || request.getUserId() == null || request.getResourceId() == null
                || request.getAction() == null || request.getAction().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户、资源和行为类型不能为空");
        }

        requireUser(request.getUserId());
        Resource resource = resourceMapper.selectById(request.getResourceId());
        if (resource == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "资源不存在");
        }

        ActionType action;
        try {
            action = ActionType.valueOf(request.getAction().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 VIEW、LIKE、FAVORITE 行为");
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
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持取消浏览行为");
            }
            shouldCancel = Boolean.TRUE.equals(request.getCancel());
        } else {
            shouldCancel = allowToggle && (action == ActionType.LIKE || action == ActionType.FAVORITE) && !existing.isEmpty();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("userId", request.getUserId());
        result.put("resourceId", request.getResourceId());
        result.put("action", action.name());

        if (shouldCancel) {
            int currentHeat = resource.getHeat() == null ? 0 : resource.getHeat();
            if (!existing.isEmpty()) {
                int deletedCount = behaviorMapper.delete(Wrappers.<UserBehavior>lambdaQuery()
                        .eq(UserBehavior::getUserId, request.getUserId())
                        .eq(UserBehavior::getResourceId, request.getResourceId())
                        .eq(UserBehavior::getAction, action));
                int decrease = (int) (action.getWeight() * (deletedCount > 0 ? deletedCount : 1));
                int newHeat = Math.max(0, currentHeat - decrease);
                resource.setHeat(newHeat);
                resourceMapper.updateById(resource);

                if (refreshProfile) {
                    profileService.recalculateProfile(request.getUserId());
                }
            }
            result.put("id", null);
            result.put("status", "CANCELLED");
            result.put("active", false);
            result.put("heat", resource.getHeat());
            result.put("createdAt", null);
            return result;
        }

        if ((action == ActionType.LIKE || action == ActionType.FAVORITE) && !existing.isEmpty()) {
            UserBehavior first = existing.get(0);
            result.put("id", first.getId());
            result.put("status", "RECORDED");
            result.put("active", true);
            result.put("heat", resource.getHeat());
            result.put("createdAt", first.getCreatedAt());
            return result;
        }

        UserBehavior behavior = UserBehavior.builder()
                .userId(request.getUserId())
                .resourceId(request.getResourceId())
                .action(action)
                .createdAt(LocalDateTime.now())
                .build();
        behaviorMapper.insert(behavior);

        int increase = (int) action.getWeight();
        int newHeat = (resource.getHeat() == null ? 0 : resource.getHeat()) + increase;
        resource.setHeat(newHeat);
        resourceMapper.updateById(resource);

        if (refreshProfile) {
            profileService.recalculateProfile(request.getUserId());
        }

        result.put("id", behavior.getId());
        result.put("status", "RECORDED");
        result.put("active", true);
        result.put("heat", resource.getHeat());
        result.put("createdAt", behavior.getCreatedAt());
        return result;
    }

    /**
     * 查询用户最近行为，并可按浏览、点赞或收藏类型在数据库侧过滤。
     *
     * @param userId 用户主键
     * @param action 可选行为类型；为空表示查询全部行为
     * @param limit 返回记录数，取值范围为 1 到 100
     * @return 按发生时间倒序排列的行为列表
     * @throws ResponseStatusException 用户不存在、行为类型非法或 limit 超出范围时抛出
     */
    public List<UserBehavior> getUserBehaviors(Long userId, ActionType action, int limit) {
        requireUser(userId);
        if (limit < 1 || limit > MAX_BEHAVIOR_LIMIT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit 必须在 1 到 100 之间");
        }
        return behaviorMapper.selectList(Wrappers.<UserBehavior>lambdaQuery()
                .eq(UserBehavior::getUserId, userId)
                .eq(action != null, UserBehavior::getAction, action)
                .orderByDesc(UserBehavior::getCreatedAt)
                .last("LIMIT " + limit));
    }

    /**
     * 统计用户各行为类型的累计次数。
     *
     * @param userId 用户主键
     * @return 以 VIEW、LIKE、FAVORITE 为键的行为次数映射
     * @throws ResponseStatusException 用户不存在时抛出 404 异常
     */
    public Map<String, Long> getBehaviorStats(Long userId) {
        requireUser(userId);
        Map<String, Long> stats = new HashMap<>();
        for (ActionType action : ActionType.values()) {
            Long count = behaviorMapper.selectCount(Wrappers.<UserBehavior>lambdaQuery()
                    .eq(UserBehavior::getUserId, userId)
                    .eq(UserBehavior::getAction, action));
            stats.put(action.name(), count == null ? 0L : count);
        }
        return stats;
    }

    /**
     * 校验用户主键是否有效且对应用户存在。
     *
     * @param userId 待校验的用户主键
     * @throws ResponseStatusException 用户主键为空或用户不存在时抛出 404 异常
     */
    private void requireUser(Long userId) {
        if (userId == null || userMapper.selectById(userId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
    }
}
