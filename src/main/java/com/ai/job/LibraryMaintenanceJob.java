package com.ai.job;

import com.ai.config.ConditionalOnModule;

import com.ai.service.library.LibraryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 资料库后台维护：AI 摘要队列消费 + 回收站到期清理。
 */
@Slf4j
@ConditionalOnModule("library")
@Component
public class LibraryMaintenanceJob {

    @Resource
    private LibraryService libraryService;

    @Scheduled(fixedDelay = 5000)
    public void pollAiParseQueue() {
        try {
            libraryService.pollAiParseQueue();
        } catch (Exception e) {
            log.error("Library AI parse poll failed", e);
        }
    }

    @Scheduled(cron = "0 40 4 * * ?")
    public void purgeExpiredRecycle() {
        try {
            libraryService.purgeExpiredRecycle();
        } catch (Exception e) {
            log.warn("Library recycle purge failed: {}", e.getMessage());
        }
    }

    /** 语义搜索向量回填：为有摘要但缺向量的历史文件补齐 */
    @Scheduled(fixedDelay = 30000, initialDelay = 60000)
    public void backfillEmbeddings() {
        try {
            libraryService.backfillEmbeddings();
        } catch (Exception e) {
            log.warn("Library embedding backfill failed: {}", e.getMessage());
        }
    }
}
