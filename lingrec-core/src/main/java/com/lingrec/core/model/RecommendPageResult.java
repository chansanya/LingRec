package com.lingrec.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 推荐会话游标分页响应数据结构，保证多批次滚动加载过程中的稳定序列呈现。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendPageResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 本次推荐会话的唯一标识，用于串联游标分页快照。
     */
    private String sessionId;

    /**
     * 本批次返回的推荐资源列表。
     */
    private List<ResourceItem> items;

    /**
     * 下一批次读取应使用的起始游标偏移量。
     */
    private int nextCursor;

    /**
     * 快照内是否还有剩余更多资源可供继续加载。
     */
    private boolean hasMore;

    /**
     * 当前推荐会话覆盖的候选排序资源总条数。
     */
    private int total;
}
