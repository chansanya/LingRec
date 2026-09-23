package com.lingrec;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
@MapperScan({"com.lingrec.mapper", "com.lingrec.starter.mapper"})
public class RecServerApplication {
    /**
     * 启动 LingRec 推荐服务并初始化 Spring 应用上下文。
     *
     * @param args JVM 传入的启动参数，可包含 Spring Boot 命令行配置
     */
    public static void main(String[] args) {
        SpringApplication.run(RecServerApplication.class, args);
    }
}
