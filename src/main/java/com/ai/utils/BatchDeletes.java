package com.ai.utils;

import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.query.QueryWrapper;

import java.util.function.Supplier;

/**
 * 日志保留期清理专用：把「按时间一刀切」的大 DELETE 切成 DELETE ... LIMIT 小批次。
 * 每批是独立短事务，避免清理积压时长事务持锁、undo 膨胀顶爆小内存机器（2G/5 连接）。
 */
public final class BatchDeletes {

    /** 单批行数：对 5 连接/2G 机器足够温和，积压十万行也只需数百批 */
    public static final int BATCH_SIZE = 500;
    /** 批间停顿，让出 IO 与连接池 */
    private static final long PAUSE_MILLIS = 50;

    private BatchDeletes() {
    }

    /**
     * 循环执行 DELETE ... LIMIT 直到删空，返回删除总行数。
     * 每批用新 QueryWrapper（wrapper 有状态，不可复用）；批间 sleep，中断时返回已删行数。
     */
    public static <T> int purge(BaseMapper<T> mapper, Supplier<QueryWrapper> condition) {
        int total = 0;
        while (true) {
            int deleted = mapper.deleteByQuery(condition.get().limit(BATCH_SIZE));
            total += deleted;
            if (deleted < BATCH_SIZE) {
                return total;
            }
            try {
                Thread.sleep(PAUSE_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return total;
            }
        }
    }
}
