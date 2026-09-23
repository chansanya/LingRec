package com.lingrec.starter.template;

import com.lingrec.core.enums.ActionType;
import com.lingrec.core.model.BehaviorRequest;
import com.lingrec.core.model.BehaviorResult;
import com.lingrec.core.model.RecommendPageResult;
import com.lingrec.core.model.UserProfileDTO;
import com.lingrec.starter.service.BehaviorService;
import com.lingrec.starter.service.ProfileService;
import com.lingrec.starter.service.RecommendService;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * LingRec 推荐引擎统一业务操作门面默认实现类。
 */
@RequiredArgsConstructor
public class DefaultLingRecTemplate implements LingRecTemplate {
    private final BehaviorService behaviorService;
    private final ProfileService profileService;
    private final RecommendService recommendService;

    @Override
    public BehaviorResult recordBehavior(Long userId, Long resourceId, ActionType action) {
        if (action == null) {
            throw new IllegalArgumentException("行为类型不能为空");
        }
        BehaviorRequest request = BehaviorRequest.builder()
                .userId(userId)
                .resourceId(resourceId)
                .action(action.name())
                .build();
        return behaviorService.recordBehavior(request);
    }

    @Override
    public BehaviorResult recordBehavior(BehaviorRequest request) {
        return behaviorService.recordBehavior(request);
    }

    @Override
    public BehaviorResult cancelBehavior(Long userId, Long resourceId, ActionType action) {
        return behaviorService.cancelBehavior(userId, resourceId, action);
    }

    @Override
    public RecommendPageResult getRecommendations(Long userId, Long categoryId) {
        return recommendService.createRecommendationSession(userId, categoryId);
    }

    @Override
    public RecommendPageResult getRecommendationPage(Long userId, String sessionId, int cursor, int size) {
        return recommendService.getRecommendationPage(userId, sessionId, cursor, size);
    }

    @Override
    public List<UserProfileDTO> getUserProfile(Long userId) {
        return profileService.getUserProfile(userId);
    }

    @Override
    public List<Long> getInteractedResourceIds(Long userId, ActionType action) {
        return behaviorService.getInteractedResourceIds(userId, action);
    }

    @Override
    public Map<String, Long> getBehaviorStats(Long userId) {
        return behaviorService.getBehaviorStats(userId);
    }
}
