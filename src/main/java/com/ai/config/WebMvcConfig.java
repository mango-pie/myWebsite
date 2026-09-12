package com.ai.config;

import com.ai.interceptor.AiRateLimitInterceptor;
import com.ai.interceptor.MaintenanceModeInterceptor;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${upload.path}")
    private String uploadPath;

    @Value("${upload.access-path}")
    private String accessPath;

    @Resource
    private MaintenanceModeInterceptor maintenanceModeInterceptor;

    @Resource
    private AiRateLimitInterceptor aiRateLimitInterceptor;

    @Resource
    private AiRateLimitProperties aiRateLimitProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadDir = Paths.get(uploadPath).toAbsolutePath().normalize();

        String uploadLocation = uploadDir.toUri().toString();

        registry.addResourceHandler(accessPath)
                .addResourceLocations(uploadLocation);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(maintenanceModeInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/doc.html",
                        "/webjars/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/uploads/**",
                        "/error"
                );

        // AI 端点限流：只挂配置声明的高成本路径，未启用时不上拦截器
        if (aiRateLimitProperties.isEnabled() && !aiRateLimitProperties.getRules().isEmpty()) {
            List<String> patterns = new ArrayList<>();
            aiRateLimitProperties.getRules().forEach(rule -> patterns.add(rule.getPattern()));
            registry.addInterceptor(aiRateLimitInterceptor)
                    .addPathPatterns(patterns);
        }
    }
}
