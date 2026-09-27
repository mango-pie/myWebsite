package com.ai.mapper.library;

import com.ai.model.dto.library.LibraryUsageAggRow;
import com.ai.model.entity.library.LibraryFile;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface LibraryFileMapper extends BaseMapper<LibraryFile> {

    /**
     * 容量聚合：与 listActiveDone 同口径（user_id + upload_status='DONE' + deleted_at IS NULL）。
     * 'DONE' 字面量须与 LibraryFileCoreService.UPLOAD_DONE 保持一致；userId 走参数绑定。
     * SUM 忽略 NULL size（等价旧内存求和按 0 计），无行时 COALESCE 归零。
     */
    @Select("SELECT COUNT(*) AS fileCount, COALESCE(SUM(size), 0) AS usedBytes "
            + "FROM library_file WHERE user_id = #{userId} "
            + "AND upload_status = 'DONE' AND deleted_at IS NULL")
    LibraryUsageAggRow selectUsageAgg(@Param("userId") Long userId);
}
