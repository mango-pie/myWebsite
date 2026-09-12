-- 业务日统计（运维可观测 Phase C）
-- 在核心库 aiscene 中执行。

CREATE TABLE IF NOT EXISTS `biz_stat_daily` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `stat_date`   DATE         NOT NULL COMMENT '业务日',
    `metric`      VARCHAR(64)  NOT NULL COMMENT '指标名',
    `dim`         VARCHAR(64)  NOT NULL DEFAULT '_' COMMENT '维度，_ 表示总计',
    `value`       BIGINT       NOT NULL DEFAULT 0 COMMENT '累加值',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_date_metric_dim` (`stat_date`, `metric`, `dim`),
    KEY `idx_metric_date` (`metric`, `stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务日统计';
