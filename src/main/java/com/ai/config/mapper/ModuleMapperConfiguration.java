package com.ai.config.mapper;

import com.ai.config.ConditionalOnModule;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * 各模块 Mapper 的下沉扫描配置。取代 {@code AiApplication} 上的全局 {@code @MapperScan}，
 * 使某模块关闭时其 Mapper 不再被注册。
 *
 * <p>约定：
 * <ul>
 *   <li>platform（User / SiteSetting 等）常驻扫描；</li>
 *   <li>其余模块随 {@code app.modules.<name>} 开关条件扫描；</li>
 *   <li>reading 的 {@code KnowledgeReadingJobMapper} 归属 knowledge 包，随 knowledge 扫描。</li>
 * </ul>
 */
@Configuration
public class ModuleMapperConfiguration {

    @Configuration
    @MapperScan("com.ai.mapper.platform")
    static class PlatformMapperConfig {
    }

    @Configuration
    @ConditionalOnModule("blog")
    @MapperScan("com.ai.mapper.blog")
    static class BlogMapperConfig {
    }

    @Configuration
    @ConditionalOnModule("knowledge")
    @MapperScan("com.ai.mapper.knowledge")
    static class KnowledgeMapperConfig {
    }

    @Configuration
    @ConditionalOnModule("chat")
    @MapperScan("com.ai.mapper.chat")
    static class ChatMapperConfig {
    }

    @Configuration
    @ConditionalOnModule("study")
    @MapperScan("com.ai.mapper.study")
    static class StudyMapperConfig {
    }

    @Configuration
    @ConditionalOnModule("diary")
    @MapperScan("com.ai.mapper.diary")
    static class DiaryMapperConfig {
    }

    @Configuration
    @ConditionalOnModule("tts")
    @MapperScan("com.ai.mapper.tts")
    static class TtsMapperConfig {
    }

    @Configuration
    @ConditionalOnModule("ops")
    @MapperScan("com.ai.mapper.ops")
    static class OpsMapperConfig {
    }

    @Configuration
    @ConditionalOnModule("app-lab")
    @MapperScan("com.ai.mapper.app")
    static class AppLabMapperConfig {
    }
}
