package com.lingrec.controller;

import com.lingrec.core.enums.ActionType;
import com.lingrec.core.model.UserProfileDTO;
import com.lingrec.service.UserService;
import com.lingrec.starter.template.LingRecTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 演示工程用户及画像视图控制器。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final LingRecTemplate lingRecTemplate;

    /**
     * 查询操作面板可切换的全部模拟用户。
     *
     * @return 包含系统内所有模拟用户实体的列表
     */
    @GetMapping("/users")
    public Object getUsers() {
        return userService.getAllUsers();
    }

    /**
     * 查询用户基础信息、行为统计、小类兴趣画像以及点赞/收藏/浏览的去重资源 ID 集合。
     *
     * @param id 用户主键
     * @return 包含 user、stats、profile、likedResourceIds、favoritedResourceIds 和 viewedResourceIds 的用户画像响应
     */
    @GetMapping("/users/{id}/profile")
    public Map<String, Object> getUserProfile(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        result.put("user", userService.getUserById(id));
        result.put("stats", lingRecTemplate.getBehaviorStats(id));
        result.put("likedResourceIds", lingRecTemplate.getInteractedResourceIds(id, ActionType.LIKE));
        result.put("favoritedResourceIds", lingRecTemplate.getInteractedResourceIds(id, ActionType.FAVORITE));
        result.put("viewedResourceIds", lingRecTemplate.getInteractedResourceIds(id, ActionType.VIEW));

        List<UserProfileDTO> profileEntries = lingRecTemplate.getUserProfile(id);
        result.put("profile", profileEntries);
        return result;
    }
}
