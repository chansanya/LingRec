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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BehaviorService {
    private static final int MAX_BEHAVIOR_LIMIT = 100;

    private final UserBehaviorMapper behaviorMapper;
    private final ResourceMapper resourceMapper;
    private final UserMapper userMapper;
    private final ProfileService profileService;

    /**
     * 记录单次用户行为，更新资源热度并立即重新计算用户画像。
     *
     * @param request 行为请求，必须包含有效的用户 ID、资源 ID 和行为类型
     * @return 已持久化并生成主键的用户行为记录
     * @throws ResponseStatusException 请求参数非法、用户不存在或资源不存在时抛出
     */
    @Transactional
    public UserBehavior recordBehavior(BehaviorRequest request) {
        return persistBehavior(request, true);
    }

    /**
     * 记录生成器构造的用户行为并更新资源热度，但延迟画像刷新以避免重复全量计算。
     *
     * @param request 行为请求，必须包含有效的用户 ID、资源 ID 和行为类型
     * @return 已持久化并生成主键的用户行为记录
     * @throws ResponseStatusException 请求参数非法、用户不存在或资源不存在时抛出
     */
    @Transactional
    public UserBehavior recordBehaviorWithoutProfileRefresh(BehaviorRequest request) {
        return persistBehavior(request, false);
    }

    /**
     * 执行行为校验、持久化和资源热度累加，并按开关决定是否刷新画像。
     *
     * @param request 待持久化的行为请求
     * @param refreshProfile true 表示行为写入后立即重算画像，false 表示由调用方批量刷新
     * @return 已持久化并生成主键的用户行为记录
     * @throws ResponseStatusException 请求参数非法、用户不存在或资源不存在时抛出
     */
    private UserBehavior persistBehavior(BehaviorRequest request, boolean refreshProfile) {
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

        UserBehavior behavior = UserBehavior.builder()
                .userId(request.getUserId())
                .resourceId(request.getResourceId())
                .action(action)
                .createdAt(LocalDateTime.now())
                .build();
        behaviorMapper.insert(behavior);

        int increase = (int) action.getWeight();
        resource.setHeat((resource.getHeat() == null ? 0 : resource.getHeat()) + increase);
        resourceMapper.updateById(resource);

        if (refreshProfile) {
            profileService.recalculateProfile(request.getUserId());
        }
        return behavior;
    }

    /**
     * 查询用户最近行为。
     *
     * @param userId 用户主键
     * @param limit 返回记录数，取值范围为 1 到 100
     * @return 按发生时间倒序排列的行为列表
     * @throws ResponseStatusException 用户不存在或 limit 超出范围时抛出
     */
    public List<UserBehavior> getUserBehaviors(Long userId, int limit) {
        requireUser(userId);
        if (limit < 1 || limit > MAX_BEHAVIOR_LIMIT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit 必须在 1 到 100 之间");
        }
        return behaviorMapper.selectList(Wrappers.<UserBehavior>lambdaQuery()
                .eq(UserBehavior::getUserId, userId)
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
