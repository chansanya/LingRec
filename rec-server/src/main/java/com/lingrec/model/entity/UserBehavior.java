package com.lingrec.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingrec.model.enums.ActionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@TableName("user_behavior")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserBehavior {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("resource_id")
    private Long resourceId;

    private ActionType action;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
