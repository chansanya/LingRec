package com.lingrec.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {
    /**
     * 创建调用 Python 推荐算法服务的 HTTP 客户端，并配置连接与读取超时。
     *
     * @param builder Spring Boot 提供的 RestTemplate 构建器
     * @return 连接超时 5 秒、读取超时 10 秒的 RestTemplate 实例
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofMillis(5000))
                .setReadTimeout(Duration.ofMillis(10000))
                .build();
    }
}
