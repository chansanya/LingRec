package com.lingrec.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户行为请求数据传输对象。
 * 用于接收前端或生成器提交的浏览、点赞或收藏操作。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BehaviorRequest {
    /**
     * 用户主键 ID。
     */
    private Long userId;

    /**
     * 资源主键 ID。
     */
    private Long resourceId;

    /**
     * 行为类型名称，对应 VIEW、LIKE、FAVORITE。
     */
    private String action;

    /**
     * 显式取消标识。
     * 为 null 时按切换模式（二次点击自动取消）；为 true 时强制取消；为 false 时强制记录。
     */
    private Boolean cancel;
}
