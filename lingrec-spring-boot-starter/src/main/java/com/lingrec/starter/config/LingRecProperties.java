package com.lingrec.starter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LingRec 推荐引擎核心配置属性绑定类。
 */
@Data
@ConfigurationProperties(prefix = "lingrec")
public class LingRecProperties {
    /**
     * 是否启用 LingRec 推荐引擎自动装配，默认 true。
     */
    private boolean enabled = true;

    /**
     * 独立部署的 Python 推荐算法服务配置。
     */
    private AlgorithmProperties algorithm = new AlgorithmProperties();

    /**
     * 推荐候选池召回与分页参数配置。
     */
    private RecommendProperties recommend = new RecommendProperties();

    /**
     * 数据存储与 SPI 接入模式配置。
     */
    private StorageProperties storage = new StorageProperties();

    /**
     * 推荐会话缓存配置。
     */
    private CacheProperties cache = new CacheProperties();

    /**
     * 算法微服务连接属性。
     */
    @Data
    public static class AlgorithmProperties {
        /**
         * 独立部署的 Python 算法服务 HTTP 地址。
         */
        private String url = "http://localhost:8000";

        /**
         * 调用算法接口的 HTTP 超时时间（毫秒）。
         */
        private int timeoutMs = 3000;
    }

    /**
     * 推荐候选池与分页会话属性。
     */
    @Data
    public static class RecommendProperties {
        /**
         * 候选池上限容量，防止加载全表。
         */
        private int candidatePoolLimit = 1000;

        /**
         * 热门候选召回配额。
         */
        private int hotCandidateLimit = 400;

        /**
         * 新鲜候选召回配额。
         */
        private int freshCandidateLimit = 200;

        /**
         * 兴趣偏好候选召回配额。
         */
        private int interestCandidateLimit = 300;

        /**
         * 推荐会话快照有效时间（秒）。
         */
        private long sessionTtlSeconds = 600;

        /**
         * 默认单批次游标分页返回条数。
         */
        private int pageSize = 20;
    }

    /**
     * 数据源模式属性。
     */
    @Data
    public static class StorageProperties {
        /**
         * 存储模式：DEFAULT_TABLES（使用内建表）或 CUSTOM_SPI（外部注入 SPI）。
         */
        private StorageMode mode = StorageMode.DEFAULT_TABLES;

        /**
         * 是否在启动时自动执行内建表 DDL 初始化。
         */
        private boolean initSchema = false;
    }

    /**
     * 存储模式枚举。
     */
    public enum StorageMode {
        /**
         * 内建标准表模式（操作 category, resource, user_behavior, user_profile 表）。
         */
        DEFAULT_TABLES,

        /**
         * 自定义 SPI 模式（接入方实现 ResourceItemProvider 挂接自有数据表）。
         */
        CUSTOM_SPI
    }

    /**
     * 会话快照缓存属性。
     */
    @Data
    public static class CacheProperties {
        /**
         * 是否优先使用 Redis 保存推荐会话快照（为 true 时优先读写 Redis，异常自动降级走本地内存 Map）。
         */
        private boolean preferRedis = true;
    }
}
