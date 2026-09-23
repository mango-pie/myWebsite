package com.ai.service.library.impl;

import cn.hutool.core.util.StrUtil;
import com.ai.config.LibraryProperties;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.mapper.library.LibraryFileMapper;
import com.ai.mapper.library.LibraryTagMapper;
import com.ai.model.dto.knowledge.KnowledgeTransferFileRequest;
import com.ai.model.dto.knowledge.KnowledgeTransferResult;
import com.ai.model.entity.library.LibraryFile;
import com.ai.model.entity.library.LibraryTag;
import com.ai.model.vo.library.LibraryFileVO;
import com.ai.service.ChatImageCaptionService;
import com.ai.service.library.LibraryStorageService;
import com.ai.service.knowledge.KnowledgeAiModelService;
import com.ai.service.knowledge.KnowledgeDocumentReaderService;
import com.ai.service.knowledge.KnowledgeTransferService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.ai.service.library.impl.LibraryFileCoreService.AI_DONE;
import static com.ai.service.library.impl.LibraryFileCoreService.AI_FAILED;
import static com.ai.service.library.impl.LibraryFileCoreService.AI_PENDING;
import static com.ai.service.library.impl.LibraryFileCoreService.AI_TEXT_EXTS;
import static com.ai.service.library.impl.LibraryFileCoreService.AI_TEXT_MAX_CHARS;
import static com.ai.service.library.impl.LibraryFileCoreService.AI_DOC_EXTS;
import static com.ai.service.library.impl.LibraryFileCoreService.IMAGE_EXTS;
import static com.ai.service.library.impl.LibraryFileCoreService.UPLOAD_DONE;

/**
 * 资料库 AI 编排：摘要队列、语义搜索、向量回填、转入知识库。
 * 外部能力（embedding/图片描述/文档解析/知识库转移）全部经 ObjectProvider 获取，
 * 对应模块关闭时相应功能降级或报错，不影响资料库本体。
 */
@Slf4j
@Service
public class LibraryAiOrchestrationService {

    @Resource
    private LibraryFileCoreService core;

    @Resource
    private LibraryFileMapper libraryFileMapper;

    @Resource
    private LibraryTagMapper libraryTagMapper;

    @Resource
    private LibraryStorageService storage;

    @Resource
    private LibraryProperties properties;

    @Resource
    private LibraryVectorService vectorService;

    @Resource
    private ObjectProvider<KnowledgeAiModelService> aiModelProvider;

    @Resource
    private ObjectProvider<KnowledgeDocumentReaderService> docReaderProvider;

    @Resource
    private ObjectProvider<ChatImageCaptionService> imageCaptionProvider;

    @Resource
    private ObjectProvider<KnowledgeTransferService> transferProvider;

    @Resource(name = "libraryAiExecutor")
    private Executor libraryAiExecutor;

    /** 同一时刻至多一个 AI 摘要任务在跑 */
    private final AtomicBoolean aiBusy = new AtomicBoolean(false);

    // ---------- AI 摘要 ----------

    public boolean regenerateAi(Long fileId, Long userId) {
        LibraryFile file = core.requireActive(fileId, userId);
        if (!core.aiEligible(file.getExt())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "该文件类型暂不支持自动摘要");
        }
        return UpdateChain.of(LibraryFile.class)
                .set(LibraryFile::getAiStatus, AI_PENDING)
                .set(LibraryFile::getAiSummary, null)
                .set(LibraryFile::getAiError, null)
                .where(LibraryFile::getId).eq(file.getId())
                .update();
    }

    public void pollAiParseQueue() {
        if (aiBusy.get()) {
            return;
        }
        LibraryFile next = libraryFileMapper.selectOneByQuery(QueryWrapper.create()
                .eq("ai_status", AI_PENDING)
                .orderBy("update_time", true)
                .limit(1));
        if (next == null) {
            return;
        }
        if (!aiBusy.compareAndSet(false, true)) {
            return;
        }
        try {
            libraryAiExecutor.execute(() -> {
                try {
                    runAiParse(next.getId());
                } finally {
                    aiBusy.set(false);
                }
            });
        } catch (RejectedExecutionException e) {
            aiBusy.set(false);
        }
    }

    private void runAiParse(Long fileId) {
        LibraryFile file = libraryFileMapper.selectOneById(fileId);
        if (file == null || !AI_PENDING.equals(file.getAiStatus())) {
            return;
        }
        try {
            String summary;
            if (IMAGE_EXTS.contains(file.getExt())) {
                ChatImageCaptionService caption = imageCaptionProvider.getIfAvailable();
                if (caption == null) {
                    markAiFailed(file, "图片描述服务未启用");
                    return;
                }
                byte[] bytes = storage.download(properties.getBucket(), file.getObjectKey());
                summary = caption.caption(bytes, core.mimeOf(file.getExt()));
            } else {
                KnowledgeAiModelService ai = aiModelProvider.getIfAvailable();
                if (ai == null) {
                    markAiFailed(file, "AI 服务未启用");
                    return;
                }
                byte[] bytes = storage.download(properties.getBucket(), file.getObjectKey());
                String text;
                if (AI_DOC_EXTS.contains(file.getExt())) {
                    KnowledgeDocumentReaderService reader = docReaderProvider.getIfAvailable();
                    if (reader == null) {
                        markAiFailed(file, "文档解析服务未启用（需开启知识库模块）");
                        return;
                    }
                    text = reader.read(bytes, file.getExt());
                } else {
                    text = new String(bytes, StandardCharsets.UTF_8);
                }
                if (text.length() > AI_TEXT_MAX_CHARS) {
                    text = text.substring(0, AI_TEXT_MAX_CHARS);
                }
                String prompt = """
                        请为以下文件内容生成不超过 120 字的中文摘要，直接输出摘要正文，不要任何前缀。

                        文件名：%s

                        内容：
                        %s
                        """.formatted(file.getFileName(), text);
                summary = ai.chat(prompt);
            }
            markAiDone(file, summary);
            embedFile(file, summary);
        } catch (Exception e) {
            log.warn("Library AI parse failed, fileId={}: {}", fileId, e.getMessage());
            markAiFailed(file, StrUtil.maxLength(e.getMessage(), 500));
        }
    }

    private void markAiDone(LibraryFile file, String summary) {
        LibraryFile patch = new LibraryFile();
        patch.setAiStatus(AI_DONE);
        patch.setAiSummary(summary);
        libraryFileMapper.updateByQuery(patch, QueryWrapper.create()
                .eq("id", file.getId())
                .eq("ai_status", AI_PENDING));
    }

    private void markAiFailed(LibraryFile file, String error) {
        LibraryFile patch = new LibraryFile();
        patch.setAiStatus(AI_FAILED);
        patch.setAiError(error);
        libraryFileMapper.updateByQuery(patch, QueryWrapper.create()
                .eq("id", file.getId())
                .eq("ai_status", AI_PENDING));
    }

    // ---------- 语义向量 ----------

    /** 摘要完成后写入语义检索向量；失败仅记日志，不影响摘要状态 */
    private void embedFile(LibraryFile file, String summary) {
        try {
            KnowledgeAiModelService ai = aiModelProvider.getIfAvailable();
            if (ai == null || StrUtil.isBlank(summary)) {
                return;
            }
            String content = StrUtil.blankToDefault(file.getFileName(), "") + "\n" + summary;
            vectorService.upsert(file.getUserId(), file.getId(), content, ai.embed(content));
        } catch (Exception e) {
            log.warn("Library embedding failed, fileId={}: {}", file.getId(), e.getMessage());
        }
    }

    public List<LibraryFileVO> semanticSearch(String keyword, Long userId, int limit) {
        if (StrUtil.isBlank(keyword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "搜索词不能为空");
        }
        KnowledgeAiModelService ai = aiModelProvider.getIfAvailable();
        if (ai == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 服务未启用，语义搜索不可用");
        }
        int topK = Math.min(Math.max(limit, 1), 50);
        List<LibraryVectorService.VectorHit> hits =
                vectorService.search(userId, ai.embed(keyword.trim()), topK);
        if (hits.isEmpty()) {
            return List.of();
        }
        Map<Long, Double> scoreById = new LinkedHashMap<>();
        for (LibraryVectorService.VectorHit hit : hits) {
            scoreById.put(hit.fileId(), hit.score());
        }
        List<LibraryFile> rows = libraryFileMapper.selectListByQuery(QueryWrapper.create()
                .eq("user_id", userId)
                .eq("upload_status", UPLOAD_DONE)
                .isNull("deleted_at")
                .in("id", scoreById.keySet()));
        List<LibraryFileVO> result = new ArrayList<>(rows.size());
        for (LibraryFile row : rows) {
            LibraryFileVO vo = core.toVo(row);
            vo.setScore(scoreById.get(row.getId()));
            result.add(vo);
        }
        result.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return result.size() > topK ? result.subList(0, topK) : result;
    }

    public int backfillEmbeddings() {
        KnowledgeAiModelService ai = aiModelProvider.getIfAvailable();
        if (ai == null || !vectorService.ping()) {
            return 0;
        }
        List<LibraryFile> candidates = libraryFileMapper.selectListByQuery(QueryWrapper.create()
                .eq("ai_status", AI_DONE)
                .isNotNull("ai_summary")
                .eq("upload_status", UPLOAD_DONE)
                .isNull("deleted_at")
                .orderBy("update_time", false)
                .limit(50));
        if (candidates.isEmpty()) {
            return 0;
        }
        List<Long> existing = vectorService.existingFileIds(
                candidates.stream().map(LibraryFile::getId).toList());
        int count = 0;
        for (LibraryFile file : candidates) {
            if (existing.contains(file.getId())) {
                continue;
            }
            embedFile(file, file.getAiSummary());
            count++;
            if (count >= 10) {
                break;
            }
        }
        return count;
    }

    // ---------- 转入知识库 ----------

    public boolean transferToKnowledge(Long fileId, Long userId) {
        core.requireActive(fileId, userId);
        if (transferProvider.getIfAvailable() == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "知识库模块未启用，无法转入");
        }
        try {
            libraryAiExecutor.execute(() -> doTransfer(fileId, userId));
        } catch (RejectedExecutionException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "后台处理队列已满，请稍后再试");
        }
        return true;
    }

    private void doTransfer(Long fileId, Long userId) {
        try {
            LibraryFile file = libraryFileMapper.selectOneById(fileId);
            if (file == null) {
                return;
            }
            KnowledgeTransferService transfer = transferProvider.getIfAvailable();
            if (transfer == null) {
                log.warn("Library transfer skipped, knowledge module disabled, fileId={}", fileId);
                return;
            }
            byte[] content = storage.download(properties.getBucket(), file.getObjectKey());
            KnowledgeTransferFileRequest request = new KnowledgeTransferFileRequest();
            request.setFileName(file.getFileName());
            request.setExt(file.getExt());
            request.setContentType(core.mimeOf(file.getExt()));
            request.setContent(content);
            request.setTags(resolveTagNames(file));
            KnowledgeTransferResult result = transfer.transferFile(request, userId);
            log.info("Library file transferred to knowledge, fileId={}, noteId={}, indexed={}",
                    fileId, result.getNoteId(), result.isIndexed());
        } catch (Exception e) {
            log.error("Library transfer-knowledge failed, fileId={}", fileId, e);
        }
    }

    private String resolveTagNames(LibraryFile file) {
        List<Long> tagIds = core.parseIds(file.getTagIds());
        if (tagIds.isEmpty()) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (LibraryTag tag : libraryTagMapper.selectListByIds(tagIds)) {
            names.add(tag.getName());
        }
        return names.isEmpty() ? null : String.join(",", names);
    }
}
