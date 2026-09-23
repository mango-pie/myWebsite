package com.ai.service.library.impl;

import cn.hutool.core.util.StrUtil;
import com.ai.config.LibraryProperties;
import com.ai.mapper.library.LibraryFileMapper;
import com.ai.model.dto.library.LibraryIdsRequest;
import com.ai.model.entity.library.LibraryFile;
import com.ai.service.library.LibraryStorageService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 资料库回收站：软删、还原、彻底清除（含 MinIO 对象与向量）。
 * 清除顺序刻意「先删库、后清外部」：DB 行删除走短事务，提交后再清理对象与向量，
 * 外部清理失败只会留下孤儿对象/向量（有日志可追），不会留下指向已删对象的悬空行。
 */
@Slf4j
@Service
public class LibraryRecycleService {

    @Resource
    private LibraryFileCoreService core;

    @Resource
    private LibraryFileMapper libraryFileMapper;

    @Resource
    private LibraryStorageService storage;

    @Resource
    private LibraryProperties properties;

    @Resource
    private LibraryVectorService vectorService;

    @Resource
    private TransactionTemplate transactionTemplate;

    public boolean deleteFiles(LibraryIdsRequest request, Long userId) {
        List<Long> ids = core.requireIds(request);
        LibraryFile patch = new LibraryFile();
        patch.setDeletedAt(LocalDate.now());
        patch.setDeleteTime(LocalDateTime.now());
        return libraryFileMapper.updateByQuery(patch, QueryWrapper.create()
                .eq("user_id", userId)
                .isNull("deleted_at")
                .in("id", ids)) > 0;
    }

    public boolean restore(LibraryIdsRequest request, Long userId) {
        List<Long> ids = core.requireIds(request);
        return UpdateChain.of(LibraryFile.class)
                .set(LibraryFile::getDeletedAt, null)
                .set(LibraryFile::getDeleteTime, null)
                .where(LibraryFile::getUserId).eq(userId)
                .and(LibraryFile::getDeletedAt).isNotNull()
                .and(LibraryFile::getId).in(ids)
                .update();
    }

    public boolean purge(LibraryIdsRequest request, Long userId) {
        List<Long> ids = core.requireIds(request);
        List<LibraryFile> rows = libraryFileMapper.selectListByQuery(QueryWrapper.create()
                .eq("user_id", userId)
                .isNotNull("deleted_at")
                .in("id", ids));
        if (rows.isEmpty()) {
            return false;
        }
        deleteRowsAndCleanup(rows);
        return true;
    }

    public int purgeExpiredRecycle() {
        LocalDate cutoff = LocalDate.now().minusDays(properties.getRecycleRetentionDays());
        List<LibraryFile> rows = libraryFileMapper.selectListByQuery(QueryWrapper.create()
                .lt("deleted_at", cutoff)
                .limit(LibraryFileCoreService.RECYCLE_LIST_LIMIT));
        if (rows.isEmpty()) {
            return 0;
        }
        deleteRowsAndCleanup(rows);
        log.info("Library recycle purge: removed {} expired files (cutoff={})", rows.size(), cutoff);
        return rows.size();
    }

    private void deleteRowsAndCleanup(List<LibraryFile> rows) {
        // purge 是单用户；purgeExpiredRecycle 可能跨用户，按用户分组处理
        Map<Long, List<LibraryFile>> byUser = rows.stream()
                .collect(Collectors.groupingBy(LibraryFile::getUserId));
        for (Map.Entry<Long, List<LibraryFile>> entry : byUser.entrySet()) {
            Long userId = entry.getKey();
            List<LibraryFile> userRows = entry.getValue();
            List<Long> ids = userRows.stream().map(LibraryFile::getId).toList();
            transactionTemplate.executeWithoutResult(status ->
                    libraryFileMapper.deleteByQuery(QueryWrapper.create()
                            .eq("user_id", userId)
                            .in("id", ids)));
            // 提交后再清外部资源；每项独立 try，一项失败不影响其余
            try {
                removeObjectsIfUnreferenced(userId, userRows);
            } catch (Exception e) {
                log.warn("Library purge MinIO cleanup failed: {}", e.getMessage());
            }
            for (LibraryFile row : userRows) {
                try {
                    vectorService.deleteByFileId(row.getId());
                } catch (Exception e) {
                    log.warn("Library purge vector cleanup failed, fileId={}: {}", row.getId(), e.getMessage());
                }
            }
        }
    }

    /**
     * 同用户下同一对象键可能被秒传去重的多条记录共享，仅在其他引用不存在时删对象。
     * 调用时机为 DB 行删除之后，剩余引用数即真实共享情况。
     */
    private void removeObjectsIfUnreferenced(Long userId, List<LibraryFile> removing) {
        Set<String> keys = new LinkedHashSet<>();
        for (LibraryFile row : removing) {
            keys.add(row.getObjectKey());
        }
        for (String objectKey : keys) {
            if (StrUtil.isBlank(objectKey)) {
                continue;
            }
            long refs = libraryFileMapper.selectCountByQuery(QueryWrapper.create()
                    .eq("user_id", userId)
                    .eq("object_key", objectKey));
            if (refs == 0) {
                storage.remove(properties.getBucket(), objectKey);
            }
        }
    }
}
