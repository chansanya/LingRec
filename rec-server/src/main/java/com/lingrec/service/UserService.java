package com.lingrec.service;

import com.lingrec.mapper.UserMapper;
import com.lingrec.model.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserMapper userMapper;

    /**
     * 查询全部用户，供操作面板初始化用户切换列表。
     *
     * @return 数据库中的全部用户实体
     */
    public List<User> getAllUsers() {
        return userMapper.selectList(null);
    }

    /**
     * 按主键查询用户。
     *
     * @param id 用户主键，可为空
     * @return 匹配的用户实体；主键为空或不存在时返回 null
     */
    public User getUserById(Long id) {
        return id == null ? null : userMapper.selectById(id);
    }
}
