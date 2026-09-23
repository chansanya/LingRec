package com.lingrec.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 行为执行结果数据传输对象，向调用端反馈当前行为状态、最新激活态与即时热度。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BehaviorResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 持久化生成的行为记录主键 ID；若本次操作为取消则返回 null。
     */
    private Long id;

    /**
     * 用户主键 ID。
     */
    private Long userId;

    /**
     * 资源主键 ID。
     */
    private Long resourceId;

    /**
     * 行为类型名称（VIEW / LIKE / FAVORITE）。
     */
    private String action;

    /**
     * 状态编码：RECORDED（已记录）或 CANCELLED（已取消）。
     */
    private String status;

    /**
     * 当前行为在该资源上是否处于激活状态（点赞/收藏为 true，已取消为 false）。
     */
    private boolean active;

    /**
     * 资源在本次行为变更后的最新热度分值。
     */
    private int heat;

    /**
     * 行为生效时间戳。
     */
    private LocalDateTime createdAt;
}
