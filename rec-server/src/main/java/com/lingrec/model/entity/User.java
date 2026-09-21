package com.lingrec.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@TableName("users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String nickname;
    private String avatar;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
