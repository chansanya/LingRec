package com.lingrec.starter.client;

import com.lingrec.core.client.AlgorithmClient;
import com.lingrec.core.model.RecommendRequest;
import com.lingrec.core.model.RecommendResponse;
import com.lingrec.core.model.ResourceItem;
import com.lingrec.core.model.UserProfileDTO;
import com.lingrec.starter.config.LingRecProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 默认基于 Spring RestTemplate 实现的 Python 算法服务通信客户端。
 */
@Slf4j
public class DefaultAlgorithmClient implements AlgorithmClient {
    private final RestTemplate restTemplate;
    private final LingRecProperties properties;

    /**
     * 构造带自适应超时机制的算法通信客户端。
     *
     * @param restTemplate 可复用的 RestTemplate 实例，为 null 时自动使用配置参数创建
     * @param properties 推荐引擎全局配置属性
     */
    public DefaultAlgorithmClient(RestTemplate restTemplate, LingRecProperties properties) {
        this.properties = properties;
        if (restTemplate != null) {
            this.restTemplate = restTemplate;
        } else {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(properties.getAlgorithm().getTimeoutMs());
            factory.setReadTimeout(properties.getAlgorithm().getTimeoutMs());
            this.restTemplate = new RestTemplate(factory);
        }
    }

    /**
     * 将用户画像和候选资源集打包推送到独立部署的 Python 算法服务，并接收返回的排序资源 ID 序列。
     *
     * @param userId 目标用户主键 ID
     * @param profile 用户分类偏好画像列表
     * @param candidates 召回的候选资源全集
     * @return 算法排好序的资源主键 ID 顺序列表；若服务离线或异常则返回空列表触发降级
     */
    @Override
    public List<Long> rank(Long userId, List<UserProfileDTO> profile, List<ResourceItem> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }

        List<RecommendRequest.ProfileItem> profileItems = profile == null ? Collections.emptyList() :
                profile.stream().map(item -> RecommendRequest.ProfileItem.builder()
                        .categoryId(item.getCategoryId())
                        .categoryName(item.getCategoryName() != null ? item.getCategoryName() : "Unknown")
                        .score(item.getScore() != null ? item.getScore() : 0.0)
                        .build())
                .collect(Collectors.toList());

        RecommendRequest request = RecommendRequest.builder()
                .userId(userId)
                .userProfile(profileItems)
                .candidates(candidates)
                .build();

        String url = properties.getAlgorithm().getUrl() + "/recommend";
        try {
            RecommendResponse response = restTemplate.postForObject(url, request, RecommendResponse.class);
            if (response != null && response.getResourceIds() != null) {
                return response.getResourceIds().stream()
                        .distinct()
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("调用远程推荐算法服务 [{}] 异常: {}, 将自动触发热度降级策略", url, e.getMessage());
        }
        return Collections.emptyList();
    }

    /**
     * 检查远程 Python 算法微服务的健康在线状态。
     *
     * @return true 表示远程算法服务健康可用，false 表示离线或无响应
     */
    @Override
    @SuppressWarnings("unchecked")
    public boolean isAvailable() {
        String healthUrl = properties.getAlgorithm().getUrl() + "/health";
        try {
            Map<String, Object> resp = restTemplate.getForObject(healthUrl, Map.class);
            return resp != null && "ok".equalsIgnoreCase(String.valueOf(resp.get("status")));
        } catch (Exception e) {
            return false;
        }
    }
}
