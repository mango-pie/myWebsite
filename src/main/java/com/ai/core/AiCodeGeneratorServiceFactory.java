package com.ai.core;

import com.ai.config.ConditionalOnModule;

import com.ai.service.AiCodeGeneratorService;
import com.ai.service.ChatHistoryService;
import com.ai.setting.IntegrationClientCache;
import com.ai.setting.IntegrationOpenAiModelFactory;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * AI 代码生成服务工厂：按 appId 缓存 langchain4j AiServices 实例，
 * 集成配置版本变化时整体失效重建。
 */
@Slf4j
@ConditionalOnModule({"app-lab", "chat"})
@Configuration
public class AiCodeGeneratorServiceFactory {

    @Resource
    private StreamingChatModel streamingChatModel;

    @Resource
    private IntegrationOpenAiModelFactory integrationOpenAiModelFactory;

    @Resource
    private IntegrationClientCache integrationClientCache;

    @Resource
    private ChatMemoryStore chatMemoryStore;

    @Resource
    private ChatHistoryService chatHistoryService;

    private volatile long cachedIntegrationVersion = -1;

    /**
     * 默认提供一个 Bean
     */
    @Bean
    public AiCodeGeneratorService aiCodeGeneratorService() {
        return getAiCodeGeneratorService(0L);
    }

    /**
     * AI 服务实例缓存
     * 缓存策略：
     * - 最大缓存 1000 个实例
     * - 写入后 30 分钟过期
     * - 访问后 10 分钟过期
     */
    private final Cache<Long, AiCodeGeneratorService> serviceCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener((key, value, cause) ->
                    log.debug("AI 服务实例被移除，appId: {}, 原因: {}", key, cause))
            .build();

    /**
     * 根据 appId 获取服务（带缓存）
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId) {
        long version = integrationClientCache.version();
        if (version != cachedIntegrationVersion) {
            serviceCache.invalidateAll();
            cachedIntegrationVersion = version;
        }
        return serviceCache.get(appId, this::createAiCodeGeneratorService);
    }

    /**
     * 创建新的 AI 服务实例
     */
    private AiCodeGeneratorService createAiCodeGeneratorService(long appId) {
        // 根据 appId 构建独立的对话记忆
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory
                .builder()
                .id(appId)
                .chatMemoryStore(chatMemoryStore)
                .maxMessages(20)
                .build();
        // 从数据库加载历史对话到记忆中
        chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, 20);
        StreamingChatModel model = resolveStreamingChatModel();
        return AiServices.builder(AiCodeGeneratorService.class)
                .streamingChatModel(model)
                .chatMemory(chatMemory)
                .build();
    }

    private StreamingChatModel resolveStreamingChatModel() {
        try {
            return integrationOpenAiModelFactory.codegenStreamingChatModel();
        } catch (Exception e) {
            return streamingChatModel;
        }
    }


}
