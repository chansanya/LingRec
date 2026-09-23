package com.lingrec.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 接收自 Python 算法服务的打分排序响应数据结构。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 算法完成特征加权与多样性重排后返回的最终推荐资源 ID 顺序列表。
     */
    private List<Long> resourceIds;

    /**
     * 算法内部采用的策略标识（例如 personalized 或 cold_start_hot）。
     */
    private String strategy;
}
