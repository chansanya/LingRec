package com.lingrec.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户行为操作请求数据传输对象。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BehaviorRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 产生行为的用户主键 ID。
     */
    private Long userId;

    /**
     * 发生交互的资源或内容主键 ID。
     */
    private Long resourceId;

    /**
     * 行为类型名称，对应 VIEW、LIKE 或 FAVORITE。
     */
    private String action;

    /**
     * 显式取消指示标识。
     * 为 null 时走自动 Toggle 模式（首次记录、二次取消）；为 true 时强制执行取消；为 false 时强制记录。
     */
    private Boolean cancel;
}
