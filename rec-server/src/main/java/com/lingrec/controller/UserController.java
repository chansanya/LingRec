package com.lingrec.controller;

import com.lingrec.mapper.CategoryMapper;
import com.lingrec.model.entity.Category;
import com.lingrec.service.BehaviorService;
import com.lingrec.service.ProfileService;
import com.lingrec.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final ProfileService profileService;
    private final BehaviorService behaviorService;
    private final CategoryMapper categoryMapper;

    /**
     * 查询操作面板可切换的全部模拟用户。
     *
     * @return 用户实体列表
     */
    @GetMapping("/users")
    public Object getUsers() {
        return userService.getAllUsers();
    }

    /**
     * 查询用户基础信息、行为统计和按资源小类计算的兴趣画像。
     *
     * @param id 用户主键
     * @return 包含 user、stats 和 profile 三部分的用户画像响应
     */
    @GetMapping("/users/{id}/profile")
    public Map<String, Object> getUserProfile(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        result.put("user", userService.getUserById(id));
        result.put("stats", behaviorService.getBehaviorStats(id));

        Map<Long, Category> categoryMap = categoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(Category::getId, category -> category));

        List<Map<String, Object>> profileEntries = profileService.getUserProfile(id).stream()
                .map(profile -> {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("categoryId", profile.getCategoryId());
                    entry.put("score", profile.getScore());
                    Category category = categoryMap.get(profile.getCategoryId());
                    if (category != null) {
                        entry.put("categoryName", category.getName());
                        Category parent = categoryMap.get(category.getParentId());
                        entry.put("parentCategoryName", parent != null ? parent.getName() : null);
                    }
                    return entry;
                })
                .collect(Collectors.toList());

        result.put("profile", profileEntries);
        return result;
    }
}
