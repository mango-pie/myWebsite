package com.ai.mapper;

import com.ai.model.entity.BizStatDaily;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

public interface BizStatDailyMapper extends BaseMapper<BizStatDaily> {

    @Insert("""
            INSERT INTO biz_stat_daily (stat_date, metric, dim, value, update_time)
            VALUES (#{statDate}, #{metric}, #{dim}, #{delta}, NOW())
            ON DUPLICATE KEY UPDATE value = value + #{delta}, update_time = NOW()
            """)
    int upsertIncrement(@Param("statDate") LocalDate statDate,
                        @Param("metric") String metric,
                        @Param("dim") String dim,
                        @Param("delta") long delta);
}
