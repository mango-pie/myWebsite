package com.ai;

import com.ai.config.redis.RedisAvailabilityListener;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@MapperScan("com.ai.mapper")
@SpringBootApplication(exclude = {dev.langchain4j.community.store.embedding.redis.spring.RedisEmbeddingStoreAutoConfiguration.class})
public class AiApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(AiApplication.class);
        app.addListeners(new RedisAvailabilityListener());
        app.run(args);
    }

}