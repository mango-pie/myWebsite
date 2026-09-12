package com.ai.mapper.ops;

import com.ai.model.dto.ops.AiUsageAggRow;
import com.ai.model.entity.AiUsageLog;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface AiUsageLogMapper extends BaseMapper<AiUsageLog> {

    String TIME_RANGE = "create_time >= #{start} AND create_time <= #{end}";

    /**
     * 全局聚合：错误判定 LOWER(status)='error' 与原 Java 端 equalsIgnoreCase 等价；
     * SUM(total_tokens) 在无任何非空值时返回 NULL，保留「未上报 token」语义。
     */
    @Select("SELECT COUNT(*) AS requestCount, "
            + "SUM(CASE WHEN LOWER(status) = 'error' THEN 1 ELSE 0 END) AS errorCount, "
            + "SUM(total_tokens) AS totalTokens, "
            + "SUM(response_time_ms) AS latencySum, "
            + "COUNT(response_time_ms) AS latencyCount "
            + "FROM ai_usage_log WHERE " + TIME_RANGE)
    AiUsageAggRow selectGlobalAgg(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT DATE(create_time) AS aggDate, COUNT(*) AS requestCount, "
            + "SUM(CASE WHEN LOWER(status) = 'error' THEN 1 ELSE 0 END) AS errorCount, "
            + "SUM(total_tokens) AS totalTokens, "
            + "SUM(response_time_ms) AS latencySum, "
            + "COUNT(response_time_ms) AS latencyCount "
            + "FROM ai_usage_log WHERE " + TIME_RANGE + " "
            + "GROUP BY DATE(create_time) ORDER BY aggDate")
    List<AiUsageAggRow> selectDailyAgg(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT COALESCE(NULLIF(scene, ''), 'other') AS aggKey, COUNT(*) AS requestCount "
            + "FROM ai_usage_log WHERE " + TIME_RANGE + " "
            + "GROUP BY COALESCE(NULLIF(scene, ''), 'other') "
            + "ORDER BY requestCount DESC, aggKey")
    List<AiUsageAggRow> selectSceneAgg(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT COALESCE(NULLIF(model_name, ''), 'unknown') AS aggKey, COUNT(*) AS requestCount, "
            + "SUM(total_tokens) AS totalTokens "
            + "FROM ai_usage_log WHERE " + TIME_RANGE + " "
            + "GROUP BY COALESCE(NULLIF(model_name, ''), 'unknown') "
            + "ORDER BY requestCount DESC, aggKey")
    List<AiUsageAggRow> selectModelAgg(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
