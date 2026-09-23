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

/**
 * 分类持久化实体。
 */
@TableName("category")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    @TableField("parent_id")
    private Long parentId;

    private String icon;

    @TableField("sort_order")
    private Integer sortOrder;
}
