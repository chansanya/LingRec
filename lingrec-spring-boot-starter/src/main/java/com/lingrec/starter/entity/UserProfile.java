package com.lingrec.starter.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户小类画像分数持久化实体。
 */
@TableName("user_profile")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfile implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("category_id")
    private Long categoryId;

    private Double score;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
