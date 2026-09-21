package com.lingrec.controller;

import com.lingrec.mapper.CategoryMapper;
import com.lingrec.model.dto.BehaviorRequest;
import com.lingrec.model.entity.Category;
import com.lingrec.model.entity.Resource;
import com.lingrec.model.entity.UserBehavior;
import com.lingrec.service.BehaviorService;
import com.lingrec.service.ResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BehaviorController {
    private final BehaviorService behaviorService;
    private final ResourceService resourceService;
    private final CategoryMapper categoryMapper;

    /**
     * 记录用户对资源的浏览、点赞或收藏行为，并触发热度和画像更新。
     *
     * @param request 行为请求，必须包含有效的用户 ID、资源 ID 和行为类型
     * @return 已持久化并生成主键的用户行为记录
     */
    @PostMapping("/behaviors")
    public UserBehavior recordBehavior(@RequestBody BehaviorRequest request) {
        return behaviorService.recordBehavior(request);
    }

    /**
     * 查询用户最近行为，并补充资源标题及父子分类信息。
     *
     * @param userId 用户主键
     * @param limit 返回记录数，取值范围为 1 到 100
     * @return 按发生时间倒序排列的行为展示数据
     */
    @GetMapping("/behaviors/{userId}")
    public List<Map<String, Object>> getBehaviors(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "20") int limit) {
        List<UserBehavior> behaviors = behaviorService.getUserBehaviors(userId, limit);
        List<Long> resourceIds = behaviors.stream()
                .map(UserBehavior::getResourceId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Resource> resourceMap = resourceIds.isEmpty()
                ? Collections.emptyMap()
                : resourceService.getByIds(resourceIds).stream()
                        .collect(Collectors.toMap(Resource::getId, Function.identity()));
        Map<Long, Category> categoryMap = categoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));

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
