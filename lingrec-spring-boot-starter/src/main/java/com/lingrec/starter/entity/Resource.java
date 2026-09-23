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
 * 资源持久化实体。
 */
@TableName("resource")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Resource implements Serializable {
    private static final long serialVersionUID = 1L;

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
