package com.ai.model.dto.library;

import lombok.Data;

/**
 * library_file SQL 聚合投影行：容量统计与 summary 共用。
 * 口径与 LibraryFileCoreService#listActiveDone 一致（user_id + upload_status='DONE' + deleted_at IS NULL）。
 */
@Data
public class LibraryUsageAggRow {

    /** COUNT(*)：active + DONE 的文件数 */
    private Long fileCount;

    /** COALESCE(SUM(size), 0)：SUM 忽略 NULL size（等价旧内存求和按 0 计），空结果归零 */
    private Long usedBytes;
}
