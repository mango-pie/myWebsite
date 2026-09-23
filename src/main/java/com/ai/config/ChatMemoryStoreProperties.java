package com.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * langchain4j Redis 聊天记忆存储的连接属性。
 * 与 Boot 自带的 RedisProperties 分开绑定，因为 ttl 是本项目的自定义扩展键。
 */
@Data
@Component
@ConfigurationProperties(prefix = "spring.data.redis")
public class ChatMemoryStoreProperties {

    private String host;

    private int port;

    private String password;

    private long ttl;
}
