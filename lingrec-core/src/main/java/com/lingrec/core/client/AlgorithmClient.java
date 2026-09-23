package com.lingrec.core.client;

import com.lingrec.core.model.ResourceItem;
import com.lingrec.core.model.UserProfileDTO;

import java.util.List;

/**
 * 推荐算法排序服务通信客户端接口。
 */
public interface AlgorithmClient {
    /**
     * 将候选资源集与用户画像打包推送到独立部署的 Python 算法服务执行特征打分与重排。
     *
     * @param userId 目标用户主键 ID
     * @param profile 用户分类偏好画像列表
     * @param candidates 召回的候选资源全集
     * @return 算法排好序的资源主键 ID 顺序列表；若服务异常可返回 null 或触发降级
     */
    List<Long> rank(Long userId, List<UserProfileDTO> profile, List<ResourceItem> candidates);

    /**
     * 检查远程算法服务健康可用状态。
     *
     * @return true 表示远程算法服务健康在线，false 表示处于离线或网络异常状态
     */
    boolean isAvailable();
}
