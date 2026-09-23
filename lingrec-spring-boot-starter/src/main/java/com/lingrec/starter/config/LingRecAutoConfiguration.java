package com.lingrec.starter.config;

import com.lingrec.core.client.AlgorithmClient;
import com.lingrec.core.spi.ResourceItemProvider;
import com.lingrec.starter.client.DefaultAlgorithmClient;
import com.lingrec.starter.mapper.CategoryMapper;
import com.lingrec.starter.mapper.ResourceMapper;
import com.lingrec.starter.mapper.UserBehaviorMapper;
import com.lingrec.starter.mapper.UserProfileMapper;
import com.lingrec.starter.provider.DefaultResourceItemProvider;
import com.lingrec.starter.service.BehaviorService;
import com.lingrec.starter.service.ProfileService;
import com.lingrec.starter.service.RecommendService;
import com.lingrec.starter.template.DefaultLingRecTemplate;
import com.lingrec.starter.template.LingRecTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.client.RestTemplate;

/**
 * LingRec 推荐引擎 Spring Boot 自动配置入口类。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "lingrec", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(LingRecProperties.class)
@MapperScan(basePackages = "com.lingrec.starter.mapper")
public class LingRecAutoConfiguration {

    /**
     * 装配默认 Python 算法通信客户端。
     *
     * @param restTemplateProvider 可选注入宿主工程已有 RestTemplate
     * @param properties 全局配置
     * @return 算法客户端实例
     */
    @Bean
    @ConditionalOnMissingBean(AlgorithmClient.class)
    public AlgorithmClient algorithmClient(ObjectProvider<RestTemplate> restTemplateProvider,
                                           LingRecProperties properties) {
        return new DefaultAlgorithmClient(restTemplateProvider.getIfAvailable(), properties);
    }

    /**
     * 装配内置标准表资源数据源 SPI 实现。
     * 若宿主工程自定义了 @Bean ResourceItemProvider，则此默认实现自动退让，不再读取内置 resource/category 表。
     *
     * @param resourceMapper 资源 Mapper
     * @param categoryMapper 分类 Mapper
     * @return 默认 ResourceItemProvider 实例
     */
    @Bean
    @ConditionalOnMissingBean(ResourceItemProvider.class)
    public ResourceItemProvider resourceItemProvider(ResourceMapper resourceMapper,
                                                     CategoryMapper categoryMapper) {
        return new DefaultResourceItemProvider(resourceMapper, categoryMapper);
    }

    /**
     * 装配用户兴趣画像计算服务（完全通过 ResourceItemProvider SPI 获取资源分类与元数据）。
     *
     * @param profileMapper 用户画像 Mapper
     * @param behaviorMapper 用户行为 Mapper
     * @param resourceItemProvider 资源数据 SPI
     * @return 画像计算服务实例
     */
    @Bean
    @ConditionalOnMissingBean(ProfileService.class)
    public ProfileService profileService(UserProfileMapper profileMapper,
                                         UserBehaviorMapper behaviorMapper,
                                         ResourceItemProvider resourceItemProvider) {
        return new ProfileService(profileMapper, behaviorMapper, resourceItemProvider);
    }

    /**
     * 装配用户行为状态机核心服务（完全通过 ResourceItemProvider SPI 回写业务资源热度）。
     *
     * @param behaviorMapper 行为 Mapper
     * @param resourceItemProvider 资源数据 SPI
     * @param profileService 画像计算服务
     * @return 行为服务实例
     */
    @Bean
    @ConditionalOnMissingBean(BehaviorService.class)
    public BehaviorService behaviorService(UserBehaviorMapper behaviorMapper,
                                           ResourceItemProvider resourceItemProvider,
                                           ProfileService profileService) {
        return new BehaviorService(behaviorMapper, resourceItemProvider, profileService);
    }

    /**
     * 装配推荐核心服务。
     *
     * @param profileService 画像计算服务
     * @param resourceItemProvider 资源数据 SPI
     * @param algorithmClient 算法客户端
     * @param properties 全局配置
     * @param redisTemplateProvider 可选注入宿主工程已有 StringRedisTemplate
     * @return 推荐服务实例
     */
    @Bean
    @ConditionalOnMissingBean(RecommendService.class)
    public RecommendService recommendService(ProfileService profileService,
                                             ResourceItemProvider resourceItemProvider,
                                             AlgorithmClient algorithmClient,
                                             LingRecProperties properties,
                                             ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        return new RecommendService(profileService, resourceItemProvider, algorithmClient, properties,
                redisTemplateProvider.getIfAvailable());
    }

    /**
     * 装配对外统一业务操作门面 LingRecTemplate。
     *
     * @param behaviorService 行为服务
     * @param profileService 画像服务
     * @param recommendService 推荐服务
     * @return 统一推荐门面实例
     */
    @Bean
    @ConditionalOnMissingBean(LingRecTemplate.class)
    public LingRecTemplate lingRecTemplate(BehaviorService behaviorService,
                                           ProfileService profileService,
                                           RecommendService recommendService) {
        return new DefaultLingRecTemplate(behaviorService, profileService, recommendService);
    }
}
