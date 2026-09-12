package com.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 有界线程池：替代裸 CompletableFuture.runAsync（ForkJoinPool.commonPool）。
 * commonPool 无界提交且共享并行度，长阻塞 AI 调用与高频日志写混跑会互相饿死。
 * 队列打满时 CallerRunsPolicy 让调用线程自己执行，形成天然背压（不丢任务）。
 */
@Configuration
public class AsyncExecutorConfig {

    /**
     * 运维日志/审计/统计落库：短 IO 任务，允许小排队，停机前尽量写完。
     */
    @Bean("opsLogExecutor")
    public ThreadPoolTaskExecutor opsLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("ops-log-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(2000);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }

    /**
     * AI 长耗时阻塞调用（知识库 SSE 问答、图片转述、智能分段）。
     * 队列刻意很小：AI 调用排队没有意义，满载时直接回落到调用线程同步执行。
     */
    @Bean("aiTaskExecutor")
    public ThreadPoolTaskExecutor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("ai-task-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(16);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
