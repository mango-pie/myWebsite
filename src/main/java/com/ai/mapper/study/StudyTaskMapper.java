package com.ai.mapper.study;

import com.mybatisflex.core.BaseMapper;
import com.ai.model.entity.StudyTask;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Map;

public interface StudyTaskMapper extends BaseMapper<StudyTask> {

    /**
     * 批量排序：单条 CASE WHEN 覆盖全部任务，替代循环内逐条 updateById。
     * sortOrders 的 value 均已由调用方解析为最终值（null 排序值已兜底为当前值），不会写入 NULL。
     * ELSE sort_order 兜底保持原值；WHERE 复刻归属校验条件（user_id + 未删除）。
     */
    @Update("<script>"
            + "UPDATE study_task SET updated_time = #{updatedTime}, sort_order = CASE id "
            + "<foreach collection='sortOrders' index='taskId' item='sortOrder'>"
            + "WHEN #{taskId} THEN #{sortOrder} "
            + "</foreach>"
            + "ELSE sort_order END "
            + "WHERE user_id = #{userId} AND deleted_time IS NULL AND id IN "
            + "<foreach collection='sortOrders' index='taskId' item='sortOrder' open='(' separator=',' close=')'>#{taskId}</foreach>"
            + "</script>")
    int updateSortOrderBatch(@Param("userId") Long userId,
                             @Param("sortOrders") Map<Long, Integer> sortOrders,
                             @Param("updatedTime") LocalDateTime updatedTime);
}
