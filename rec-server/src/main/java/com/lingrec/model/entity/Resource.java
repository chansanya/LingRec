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

@TableName("resource")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Resource {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;
    private String description;

    @TableField("category_id")
    private Long categoryId;

    @TableField("cover_url")
    private String coverUrl;

    private Integer heat;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
