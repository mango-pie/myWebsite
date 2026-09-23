package com.ai.config;

import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.Resource;

/**
 * 聊天记忆存储 Bean 装配；连接属性见 {@link ChatMemoryStoreProperties}。
 */
@Configuration
public class ChatMemoryStoreConfig {

    @Resource
    private ChatMemoryStoreProperties chatMemoryStoreProperties;

    @Bean
    @ConditionalOnProperty(name = "app.redis.available", havingValue = "true")
    public ChatMemoryStore redisChatMemoryStore() {
        return RedisChatMemoryStore.builder()
                .host(chatMemoryStoreProperties.getHost())
                .port(chatMemoryStoreProperties.getPort())
                .password(chatMemoryStoreProperties.getPassword())
                .ttl(chatMemoryStoreProperties.getTtl())
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.redis.available", havingValue = "false", matchIfMissing = true)
    public ChatMemoryStore inMemoryChatMemoryStore() {
        return new InMemoryChatMemoryStore();
    }
}
