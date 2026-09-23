package com.lingrec.controller;

import com.lingrec.core.enums.ActionType;
import com.lingrec.core.model.BehaviorRequest;
import com.lingrec.core.model.BehaviorResult;
import com.lingrec.service.ResourceService;
import com.lingrec.starter.entity.Category;
import com.lingrec.starter.entity.Resource;
import com.lingrec.starter.entity.UserBehavior;
import com.lingrec.starter.mapper.CategoryMapper;
import com.lingrec.starter.service.BehaviorService;
import com.lingrec.starter.template.LingRecTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用户行为控制层 REST 接口。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BehaviorController {
    private final LingRecTemplate lingRecTemplate;
    private final BehaviorService behaviorService;
    private final ResourceService resourceService;
    private final CategoryMapper categoryMapper;

    /**
     * 记录用户行为，点赞与收藏支持二次点击自动取消，并同步更新资源热度与用户画像。
     *
     * @param request 行为请求，必须包含用户 ID、资源 ID 和行为类型，可携带显式 cancel 标识
     * @return 包含行为执行状态（RECORDED/CANCELLED）、当前激活态、更新后热度等字段的响应数据
     */
    @PostMapping("/behaviors")
    public BehaviorResult recordBehavior(@RequestBody BehaviorRequest request) {
        return lingRecTemplate.recordBehavior(request);
    }

    /**
     * 显式取消指定用户的点赞或收藏行为，扣减资源热度并同步更新兴趣画像。
     *
     * @param userId 用户主键，不能为空
     * @param resourceId 资源主键，不能为空
     * @param action 待取消的行为类型，仅支持 LIKE 或 FAVORITE
     * @return 包含取消状态、当前激活态及更新后热度的结构化响应
     * @throws ResponseStatusException 行为类型为空或不支持时抛出 400 异常
     */
    @DeleteMapping("/behaviors")
    public BehaviorResult cancelBehavior(
            @RequestParam Long userId,
            @RequestParam Long resourceId,
            @RequestParam String action) {
        if (action == null || action.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "行为类型不能为空");
        }
        ActionType actionType;
        try {
            actionType = ActionType.valueOf(action.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 LIKE、FAVORITE 行为");
        }
        return lingRecTemplate.cancelBehavior(userId, resourceId, actionType);
    }

    /**
     * 按可选行为类型查询用户最近记录，并补充资源标题及父子分类信息。
     *
     * @param userId 用户主键
     * @param action 可选行为类型；为空查询全部，仅支持 VIEW、LIKE、FAVORITE
     * @param limit 返回记录数，取值范围为 1 到 100
     * @return 按发生时间倒序排列的行为展示数据
     * @throws ResponseStatusException 行为类型不支持或参数校验失败时抛出
     */
    @GetMapping("/behaviors/{userId}")
    public List<Map<String, Object>> getBehaviors(
            @PathVariable Long userId,
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "20") int limit) {
        ActionType actionType = null;
        if (action != null && !action.trim().isEmpty()) {
            try {
                actionType = ActionType.valueOf(action.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 VIEW、LIKE、FAVORITE 行为");
            }
        }

        List<UserBehavior> behaviors = behaviorService.getUserBehaviors(userId, actionType, limit);
        List<Long> resourceIds = behaviors.stream()
                .map(UserBehavior::getResourceId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, Resource> resourceMap = resourceIds.isEmpty()
                ? Collections.emptyMap()
                : resourceService.getByIds(resourceIds).stream()
                        .collect(Collectors.toMap(Resource::getId, Function.identity()));

        Map<Long, Category> categoryMap = categoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(Category::getId, category -> category));

        return behaviors.stream().map(behavior -> {
            Map<String, Object> result = new HashMap<>();
            result.put("id", behavior.getId());
            result.put("userId", behavior.getUserId());
            result.put("resourceId", behavior.getResourceId());
            result.put("action", behavior.getAction());
            result.put("createdAt", behavior.getCreatedAt());

            Resource resource = resourceMap.get(behavior.getResourceId());
            if (resource != null) {
                result.put("resourceTitle", resource.getTitle());
                Category category = categoryMap.get(resource.getCategoryId());
                if (category != null) {
                    result.put("categoryName", category.getName());
                    Category parent = categoryMap.get(category.getParentId());
                    result.put("parentCategoryName", parent != null ? parent.getName() : null);
                }
            }
            return result;
        }).collect(Collectors.toList());
    }
}
