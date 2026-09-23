package com.lingrec.starter.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingrec.core.enums.ActionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户行为明细持久化实体。
 */
@TableName("user_behavior")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserBehavior implements Serializable {
    private static final long serialVersionUID = 1L;

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
