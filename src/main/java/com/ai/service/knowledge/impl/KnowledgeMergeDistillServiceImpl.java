package com.ai.service.knowledge.impl;

import com.ai.config.ConditionalOnModule;

import cn.hutool.core.util.StrUtil;
import com.ai.constant.AiUsageSceneConstant;
import com.ai.constant.BizStatMetricConstant;
import com.ai.constant.knowledge.KnowledgeNoteConstant;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.mapper.knowledge.KnowledgeNoteMapper;
import com.ai.model.dto.knowledge.KnowledgeIngestBatchUrlRequest;
import com.ai.model.entity.knowledge.KnowledgeNote;
import com.ai.model.entity.knowledge.SourceDocument;
import com.ai.model.vo.knowledge.KnowledgeIngestBatchUrlVO;
import com.ai.ops.AiUsageCallContext;
import com.ai.service.BizStatDailyService;
import com.ai.service.knowledge.KnowledgeIngestionService;
import com.ai.service.knowledge.KnowledgeMergeDistillService;
import com.ai.setting.runtime.ReadingRuntimeSettings;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

@ConditionalOnModule("knowledge")
@Service
public class KnowledgeMergeDistillServiceImpl implements KnowledgeMergeDistillService {

    private static final int MAX_MERGE_RAW_CHARS = 600_000;
    private static final int MAX_URLS = 5;

    @Resource
    private KnowledgeDeepSeekReadingService deepSeekReadingService;

    @Resource
    private KnowledgeIngestionService ingestionService;

    @Resource
    private KnowledgeNoteMapper knowledgeNoteMapper;

    @Resource
    private ReadingRuntimeSettings readingRuntimeSettings;

    @Resource
    private BizStatDailyService bizStatDailyService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeIngestBatchUrlVO mergeDistill(KnowledgeIngestBatchUrlRequest request, Long userId) {
        return mergeDistill(request, userId, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeIngestBatchUrlVO mergeDistill(KnowledgeIngestBatchUrlRequest request,
                                                 Long userId,
                                                 Consumer<String> progressCallback) {
        if (request == null || request.getUrls() == null || request.getUrls().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "urls 不能为空");
        }
        List<String> urls = request.getUrls().stream()
                .filter(StrUtil::isNotBlank)
                .map(String::trim)
                .distinct()
                .toList();
        if (urls.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "urls 不能为空");
        }
        if (urls.size() > MAX_URLS) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "单次最多批量精炼 " + MAX_URLS + " 个 URL");
        }
        if (StrUtil.isNotBlank(request.getDistillPrompt()) && request.getDistillPrompt().length() > 16_000) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "distillPrompt 过长");
        }

        KnowledgeIngestBatchUrlVO vo = new KnowledgeIngestBatchUrlVO();
        vo.setTotal(urls.size());
        String agentQuery = StrUtil.trim(request.getAgentQuery());
        notifyProgress(progressCallback, "READING");
        KnowledgeDeepSeekReadingService.MaterialBundle bundle =
                deepSeekReadingService.materialize(urls, agentQuery);
        List<KnowledgeDeepSeekReadingService.MaterialSource> successes = bundle.usedSources();
        for (KnowledgeDeepSeekReadingService.MaterialSource failed : bundle.failedSources()) {
            addFailed(vo, failed.url(), failed.status(), failed.reason());
        }

        if (successes.isEmpty()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "AI 未读取到有效文章来源，未能生成精读");
        }

        vo.setUsedCount(successes.size());
        vo.setFailCount(vo.getFailedSources().size());
        for (KnowledgeDeepSeekReadingService.MaterialSource source : successes) {
            KnowledgeIngestBatchUrlVO.UsedSource used = new KnowledgeIngestBatchUrlVO.UsedSource();
            used.setUrl(source.url());
            used.setTitle(source.title());
            used.setBodyChars(source.bodyMarkdown().length());
            vo.getUsedSources().add(used);
        }

        TruncatedMerge truncated = buildMergedRaw(successes, agentQuery);
        if (truncated.truncated() && vo.getFailCount() > 0) {
            vo.setWarning("AI 读取到的材料过长已截断；部分来源读取失败，已基于可用来源生成");
        } else if (truncated.truncated()) {
            vo.setWarning("AI 读取到的材料过长，已截断部分来源");
        } else if (vo.getFailCount() > 0) {
            vo.setWarning("部分来源读取失败，已基于可用来源生成");
        }

        String title = StrUtil.isNotBlank(agentQuery)
                ? "精读合集：" + (agentQuery.length() > 40 ? agentQuery.substring(0, 40) + "…" : agentQuery)
                : "精读合集（" + successes.size() + " 篇来源）";
        String primaryUrl = successes.get(0).url();

        SourceDocument source = ingestionService.ingestAgentResult(
                title, primaryUrl, truncated.rawText(), null, userId);

        notifyProgress(progressCallback, "DISTILLING");
        String markdown = mergeDistillChat(truncated.rawText(), agentQuery,
                request.getDistillPrompt(), userId, source.getId());
        KnowledgeNote note = createNote(source, title, markdown, mergeTags(request.getTags(), agentQuery));

        vo.setSuccess(true);
        vo.setNoteId(note.getId());
        vo.setTitle(note.getTitle());
        return vo;
    }

    private void notifyProgress(Consumer<String> progressCallback, String progress) {
        if (progressCallback != null) {
            progressCallback.accept(progress);
        }
    }

    private TruncatedMerge buildMergedRaw(List<KnowledgeDeepSeekReadingService.MaterialSource> successes,
                                          String agentQuery) {
        StringBuilder sb = new StringBuilder();
        if (StrUtil.isNotBlank(agentQuery)) {
            sb.append("学习目标：").append(agentQuery).append("\n\n");
        }
        sb.append("以下为 DeepSeek 按用户勾选 URL 读取并物化的网页材料，请综合成一篇精读文章。\n\n");
        boolean truncated = false;
        int used = 0;
        for (int i = 0; i < successes.size(); i++) {
            KnowledgeDeepSeekReadingService.MaterialSource s = successes.get(i);
            String block = """
                    ## 来源 %d
                    - 标题：%s
                    - URL：%s

                    ### 正文材料
                    %s
                    ---

                    """.formatted(i + 1, s.title(), s.url(), s.bodyMarkdown());
            if (sb.length() + block.length() > MAX_MERGE_RAW_CHARS && used > 0) {
                truncated = true;
                break;
            }
            if (sb.length() + block.length() > MAX_MERGE_RAW_CHARS) {
                int remain = MAX_MERGE_RAW_CHARS - sb.length();
                if (remain > 200) {
                    sb.append(block, 0, Math.min(block.length(), remain));
                }
                truncated = true;
                used++;
                break;
            }
            sb.append(block);
            used++;
        }
        if (used < successes.size()) {
            truncated = true;
        }
        return new TruncatedMerge(sb.toString(), truncated);
    }

    private String mergeDistillChat(String mergedRaw,
                                    String agentQuery,
                                    String promptOverride,
                                    Long userId,
                                    Long sourceId) {
        String customPrompt = StrUtil.blankToDefault(StrUtil.trim(promptOverride),
                readingRuntimeSettings.distillSystemPrompt());
        String systemPrompt = """
                你是严格的技术精读重构助手。你只能使用用户提供的「已物化网页材料」写作。
                硬性约束（优先级高于用户自定义 Prompt）：
                1. 禁止编造材料中没有出现的 API、配置、命令、数字、结论和引用。
                2. 「代码/配置精髓」只能引用材料中出现过的代码、配置或命令；没有就明确写「材料未提供可验证示例」。
                3. 「参考来源」只能列出材料中出现的标题与 URL。
                4. 如果材料之间有冲突，要指出冲突，不要自行裁决。
                5. 输出必须是 Markdown，不要解释你的执行过程。
                学习目标：%s

                用户自定义蒸馏 Prompt：
                %s
                """.formatted(StrUtil.blankToDefault(agentQuery, "无明确目标"), customPrompt);
        String content = mergedRaw.length() > MAX_MERGE_RAW_CHARS
                ? mergedRaw.substring(0, MAX_MERGE_RAW_CHARS)
                : mergedRaw;
        AiUsageCallContext.set(userId, AiUsageSceneConstant.DISTILL, sourceId, "merge-distill");
        try {
            return deepSeekReadingService.distill(
                    "多来源材料：\n" + content,
                    systemPrompt,
                    readingRuntimeSettings.distillTemperature(),
                    readingRuntimeSettings.distillMaxTokens()
            );
        } finally {
            AiUsageCallContext.clear();
        }
    }

    private KnowledgeNote createNote(SourceDocument source, String title, String markdown, String tags) {
        LocalDateTime now = LocalDateTime.now();
        KnowledgeNote note = new KnowledgeNote();
        note.setSourceDocumentId(source.getId());
        note.setTitle(title);
        note.setDistilledMd(markdown);
        note.setTags(tags);
        note.setStatus(KnowledgeNoteConstant.STATUS_SUCCESS);
        note.setPublishStatus(KnowledgeNoteConstant.PUBLISH_NOT_PUBLISHED);
        note.setIndexStatus(KnowledgeNoteConstant.INDEX_NOT_INDEXED);
        note.setViewCount(0);
        note.setLastDistilledAt(now);
        note.setCreateTime(now);
        note.setUpdateTime(now);
        note.setIsDelete(0);
        knowledgeNoteMapper.insert(note);
        bizStatDailyService.increment(BizStatMetricConstant.READING_DISTILL_SUCCESS, 1);
        return note;
    }

    private String mergeTags(String tags, String agentQuery) {
        if (StrUtil.isBlank(agentQuery)) {
            return tags;
        }
        String marker = "agentQuery:" + agentQuery.trim();
        if (StrUtil.isBlank(tags)) {
            return marker;
        }
        return tags.trim() + "," + marker;
    }

    private void addFailed(KnowledgeIngestBatchUrlVO vo, String url, String reasonCode, String message) {
        KnowledgeIngestBatchUrlVO.FailedSource failed = new KnowledgeIngestBatchUrlVO.FailedSource();
        failed.setUrl(url);
        failed.setReasonCode(reasonCode);
        failed.setErrorMessage(message);
        vo.getFailedSources().add(failed);
    }

    private record TruncatedMerge(String rawText, boolean truncated) {
    }
}
