package com.ai.controller;

import com.ai.config.ConditionalOnModule;

import com.ai.annotation.AuthCheck;
import com.ai.common.BaseResponse;
import com.ai.common.ResultUtils;
import com.ai.constant.UserConstant;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.model.dto.knowledge.KnowledgeIngestBatchUrlRequest;
import com.ai.model.dto.knowledge.KnowledgeIngestFileRequest;
import com.ai.model.dto.knowledge.KnowledgeIngestUrlRequest;
import com.ai.model.dto.knowledge.KnowledgeNoteArchiveRequest;
import com.ai.model.dto.knowledge.KnowledgeNoteIndexRequest;
import com.ai.model.dto.knowledge.KnowledgeNotePublishRequest;
import com.ai.model.dto.knowledge.KnowledgeNoteQueryRequest;
import com.ai.model.dto.knowledge.KnowledgeNoteReviewRequest;
import com.ai.model.dto.knowledge.KnowledgeNoteUpdateRequest;
import com.ai.model.dto.knowledge.KnowledgeOutlineConfirmRequest;
import com.ai.model.dto.knowledge.KnowledgeProcessRequest;
import com.ai.model.dto.knowledge.KnowledgeReadingJobQueryRequest;
import com.ai.model.dto.knowledge.KnowledgeSearchPreviewRequest;
import com.ai.model.dto.knowledge.KnowledgeTagRenameRequest;
import com.ai.model.dto.knowledge.SearchOptions;
import com.ai.model.entity.User;
import com.ai.model.entity.knowledge.KnowledgeNote;
import com.ai.model.entity.knowledge.SourceDocument;
import com.ai.model.vo.blog.BlogPostVO;
import com.ai.model.vo.knowledge.KnowledgeDocumentVO;
import com.ai.model.vo.knowledge.KnowledgeNoteDetailVO;
import com.ai.model.vo.knowledge.KnowledgeNoteVO;
import com.ai.model.vo.knowledge.KnowledgeReadingJobVO;
import com.ai.model.vo.knowledge.KnowledgeSearchPreviewVO;
import com.ai.model.vo.knowledge.ReadingSkillVO;
import com.ai.model.vo.knowledge.SearchResult;
import com.ai.service.UserService;
import com.ai.service.knowledge.KnowledgeAiModelService;
import com.ai.service.knowledge.KnowledgeDistillationService;
import com.ai.service.knowledge.KnowledgeIngestionService;
import com.ai.service.knowledge.KnowledgeNoteIndexService;
import com.ai.service.knowledge.KnowledgeNotePublishService;
import com.ai.service.knowledge.KnowledgeNoteService;
import com.ai.service.knowledge.KnowledgeReadingJobService;
import com.ai.service.knowledge.KnowledgeSearchStrategy;
import com.ai.service.knowledge.LearningTreeGateService;
import com.ai.service.knowledge.support.ReadingJobNotifier;
import com.ai.service.knowledge.support.ReadingSkillLoader;
import com.ai.setting.runtime.ReadingRuntimeSettings;
import com.mybatisflex.core.paginate.Page;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ConditionalOnModule("knowledge")
@RestController
@RequestMapping("/admin/knowledge")
public class KnowledgeAdminController {

    @Resource
    private UserService userService;

    @Resource
    private KnowledgeIngestionService ingestionService;

    @Resource
    private KnowledgeDistillationService distillationService;

    @Resource
    private KnowledgeReadingJobService knowledgeReadingJobService;

    @Resource
    private KnowledgeNoteService knowledgeNoteService;

    @Resource
    private KnowledgeNotePublishService knowledgeNotePublishService;

    @Resource
    private KnowledgeNoteIndexService knowledgeNoteIndexService;

    @Resource
    private KnowledgeAiModelService aiModelService;

    @Resource
    private ReadingRuntimeSettings readingRuntimeSettings;

    @Resource
    private java.util.List<KnowledgeSearchStrategy> knowledgeSearchStrategies;

    @Resource
    private LearningTreeGateService learningTreeGateService;

    @Resource
    private ReadingJobNotifier readingJobNotifier;

    @PostMapping("/ingest/url")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<KnowledgeNoteDetailVO> ingestUrl(@RequestBody KnowledgeIngestUrlRequest request,
                                                         HttpServletRequest httpRequest) {
        requireSyncIngest();
        User loginUser = userService.getLoginUser(httpRequest);
        SourceDocument source = ingestionService.ingestUrl(request, loginUser.getId());
        KnowledgeNote note = distillationService.distillToMarkdown(
                source.getId(), loginUser.getId(), mergeTags(request));
        return ResultUtils.success(knowledgeNoteService.getDetail(note.getId(), loginUser.getId()));
    }

    @PostMapping("/ingest/batch-url")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<KnowledgeReadingJobVO> ingestBatchUrl(@RequestBody KnowledgeIngestBatchUrlRequest request,
                                                              HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        if (request != null && request.getDomainId() != null) {
            learningTreeGateService.validateTreeModeV2Access(
                    loginUser.getId(),
                    request.getDomainId(),
                    request.getGatePassId(),
                    request.getBranchId(),
                    request.getSkipGate());
        }
        if (readingRuntimeSettings.ingestAsync()) {
            return ResultUtils.success(knowledgeReadingJobService.submit(request, loginUser.getId()));
        }
        return ResultUtils.success(knowledgeReadingJobService.runSync(request, loginUser.getId()));
    }

    @GetMapping("/reading-jobs")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<KnowledgeReadingJobVO>> pageReadingJobs(KnowledgeReadingJobQueryRequest request,
                                                                     HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(knowledgeReadingJobService.page(request, loginUser.getId()));
    }

    /** 异步搜索：立即返回 jobId，后台 SEARCHING → AWAITING_SELECT */
    @PostMapping("/reading-jobs/search")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<KnowledgeReadingJobVO> submitReadingSearch(@RequestBody KnowledgeSearchPreviewRequest request,
                                                                   HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        if (request != null && request.getDomainId() != null) {
            learningTreeGateService.validateTreeModeV2Access(
                    loginUser.getId(),
                    request.getDomainId(),
                    request.getGatePassId(),
                    request.getBranchId(),
                    request.getSkipGate());
        }
        return ResultUtils.success(knowledgeReadingJobService.submitSearch(request, loginUser.getId()));
    }

    @GetMapping("/reading-jobs/{jobId}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<KnowledgeReadingJobVO> getReadingJob(@PathVariable Long jobId,
                                                             HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(knowledgeReadingJobService.getJob(jobId, loginUser.getId()));
    }

    /** 失败任务差量重试：仅重读失败来源（failedSources），上限 1 次；不重复校验门闩 */
    @PostMapping("/reading-jobs/{jobId}/retry")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<KnowledgeReadingJobVO> retryReadingJob(@PathVariable Long jobId,
                                                               HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(knowledgeReadingJobService.retry(jobId, loginUser.getId()));
    }

    /** 大纲确认步：确认后重新入队，蒸馏按该大纲对齐输出 */
    @PostMapping("/reading-jobs/{jobId}/confirm-outline")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<KnowledgeReadingJobVO> confirmReadingJobOutline(@PathVariable Long jobId,
                                                                        @RequestBody(required = false) KnowledgeOutlineConfirmRequest request,
                                                                        HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(knowledgeReadingJobService.confirmOutline(
                jobId, request == null ? null : request.getOutline(), loginUser.getId()));
    }

    /** 精读任务状态推送（SSE）：WAITING / SUCCESS / FAILED；轮询兜底仍可用 */
    @GetMapping(value = "/reading-jobs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public SseEmitter streamReadingJobs(HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return readingJobNotifier.subscribe(loginUser.getId());
    }

    @PostMapping(value = "/ingest/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<KnowledgeNoteDetailVO> ingestFile(@RequestPart("file") MultipartFile file,
                                                          KnowledgeIngestFileRequest request,
                                                          HttpServletRequest httpRequest) {
        requireSyncIngest();
        User loginUser = userService.getLoginUser(httpRequest);
        SourceDocument source = ingestionService.ingestFile(file, request, loginUser.getId());
        String tags = request == null ? null : request.getTags();
        KnowledgeNote note = distillationService.distillToMarkdown(source.getId(), loginUser.getId(), tags);
        return ResultUtils.success(knowledgeNoteService.getDetail(note.getId(), loginUser.getId()));
    }

    @GetMapping("/notes")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<KnowledgeNoteVO>> pageNotes(KnowledgeNoteQueryRequest request,
                                                         HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(knowledgeNoteService.page(request, loginUser.getId()));
    }

    @GetMapping("/notes/{noteId}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<KnowledgeNoteDetailVO> getNote(@PathVariable Long noteId, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(knowledgeNoteService.getDetail(noteId, loginUser.getId()));
    }

    @PutMapping("/notes/{noteId}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<KnowledgeNoteVO> updateNote(@PathVariable Long noteId,
                                                    @RequestBody KnowledgeNoteUpdateRequest request,
                                                    HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(knowledgeNoteService.update(noteId, request, loginUser.getId()));
    }

    @DeleteMapping("/notes/{noteId}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteNote(@PathVariable Long noteId, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(knowledgeNoteService.delete(noteId, loginUser.getId()));
    }

    /** 复习状态流转：NEW / REVIEWING / MASTERED */
    @PutMapping("/notes/{noteId}/review-status")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<KnowledgeNoteVO> updateNoteReviewStatus(@PathVariable Long noteId,
                                                                @RequestBody KnowledgeNoteReviewRequest request,
                                                                HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(knowledgeNoteService.updateReviewStatus(
                noteId, request == null ? null : request.getReviewStatus(), loginUser.getId()));
    }

    /** 归档 / 取消归档（列表默认隐藏归档） */
    @PutMapping("/notes/{noteId}/archive")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<KnowledgeNoteVO> archiveNote(@PathVariable Long noteId,
                                                     @RequestBody KnowledgeNoteArchiveRequest request,
                                                     HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        boolean archived = request != null && Boolean.TRUE.equals(request.getArchived());
        return ResultUtils.success(knowledgeNoteService.archive(noteId, archived, loginUser.getId()));
    }

    /** 整库 Markdown 批量导出（zip 备份） */
    @GetMapping(value = "/notes/export", produces = "application/zip")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public ResponseEntity<byte[]> exportNotes(HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        byte[] data = knowledgeNoteService.exportAll(loginUser.getId());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=knowledge-notes-" + java.time.LocalDate.now() + ".zip")
                .body(data);
    }

    /** 标签统计 */
    @GetMapping("/notes/tags")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<java.util.List<com.ai.model.vo.knowledge.KnowledgeTagVO>> listNoteTags(HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(knowledgeNoteService.listTags(loginUser.getId()));
    }

    /** 标签重命名（合并） */
    @PutMapping("/notes/tags/rename")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Integer> renameNoteTag(@RequestBody KnowledgeTagRenameRequest request,
                                               HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(knowledgeNoteService.renameTag(
                request == null ? null : request.getOldName(),
                request == null ? null : request.getNewName(),
                loginUser.getId()));
    }

    /** 删除标签 */
    @DeleteMapping("/notes/tags/{name}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Integer> deleteNoteTag(@PathVariable String name, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(knowledgeNoteService.deleteTag(name, loginUser.getId()));
    }

    @PostMapping("/notes/{noteId}/redistill")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<KnowledgeNoteDetailVO> redistill(@PathVariable Long noteId, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        KnowledgeNote note = distillationService.redistill(noteId, loginUser.getId());
        return ResultUtils.success(knowledgeNoteService.getDetail(note.getId(), loginUser.getId()));
    }

    @PostMapping("/notes/{noteId}/publish-blog")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<BlogPostVO> publishBlog(@PathVariable Long noteId,
                                                @RequestBody KnowledgeNotePublishRequest request,
                                                HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(knowledgeNotePublishService.publishToBlog(noteId, request, loginUser.getId()));
    }

    @PostMapping("/notes/{noteId}/sync-blog")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<BlogPostVO> syncBlog(@PathVariable Long noteId, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(knowledgeNotePublishService.syncToBlog(noteId, loginUser.getId()));
    }

    @PostMapping("/notes/{noteId}/index")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<KnowledgeDocumentVO> indexNote(@PathVariable Long noteId,
                                                       @RequestBody KnowledgeNoteIndexRequest request,
                                                       HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(knowledgeNoteIndexService.indexToKnowledgeBase(noteId, request, loginUser.getId()));
    }

    @PostMapping("/notes/{noteId}/reindex")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<KnowledgeDocumentVO> reindexNote(@PathVariable Long noteId, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(knowledgeNoteIndexService.reindex(noteId, loginUser.getId()));
    }

    @GetMapping("/reading-skill")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<ReadingSkillVO> getReadingSkill() {
        ReadingSkillVO vo = new ReadingSkillVO();
        vo.setName(ReadingSkillLoader.name());
        vo.setDescription(ReadingSkillLoader.description());
        vo.setBody(ReadingSkillLoader.body());
        return ResultUtils.success(vo);
    }

    @PostMapping("/search/preview")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<KnowledgeSearchPreviewVO> searchPreview(@RequestBody KnowledgeSearchPreviewRequest request,
                                                                HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        if (request != null && request.getDomainId() != null) {
            learningTreeGateService.validateTreeModeV2Access(
                    loginUser.getId(),
                    request.getDomainId(),
                    request.getGatePassId(),
                    request.getBranchId(),
                    request.getSkipGate());
        }
        KnowledgeSearchPreviewVO vo = new KnowledgeSearchPreviewVO();
        String goal = request == null ? "" : request.getGoal();
        if (cn.hutool.core.util.StrUtil.isBlank(goal)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "学习目标 goal 不能为空");
        }
        String preference = request == null ? null : request.getPreference();
        vo.setGoal(goal);
        boolean treeMode = request != null && request.getDomainId() != null;
        if (!treeMode) {
            vo.setOutline(buildOutline(goal, preference));
        }
        KnowledgeSearchStrategy strategy = resolveSearchStrategy();
        SearchOptions options = SearchOptions.builder().preference(preference).build();
        java.util.List<SearchResult> results = strategy.search(goal, options);
        java.util.List<KnowledgeSearchPreviewVO.Candidate> candidates = new java.util.ArrayList<>();
        for (SearchResult result : results) {
            KnowledgeSearchPreviewVO.Candidate c = new KnowledgeSearchPreviewVO.Candidate();
            c.setTitle(result.getTitle());
            c.setUrl(result.getUrl());
            c.setSummary(result.getSummary());
            c.setSource(result.getSource());
            c.setScore(result.getScore());
            c.setRecommendReason(result.getRecommendReason());
            c.setRiskFlags(result.getRiskFlags() == null ? java.util.List.of() : result.getRiskFlags());
            candidates.add(c);
        }
        vo.setCandidates(candidates);
        return ResultUtils.success(vo);
    }

    /** 兼容旧接口：URL 采集 + 可选精炼 */
    @PostMapping("/process")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<SourceDocument> processUrl(@RequestBody KnowledgeProcessRequest request,
                                                   HttpServletRequest httpRequest) {
        requireSyncIngest();
        User loginUser = userService.getLoginUser(httpRequest);
        SourceDocument source = ingestionService.ingestUrl(request, loginUser.getId());
        if (Boolean.TRUE.equals(request.getGenerateSummary())) {
            distillationService.distillToMarkdown(source.getId(), loginUser.getId(), request.getTags());
        }
        return ResultUtils.success(source);
    }

    @PostMapping("/{sourceDocumentId}/distill")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<KnowledgeNote> distill(@PathVariable Long sourceDocumentId,
                                               HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(distillationService.distillToMarkdown(sourceDocumentId, loginUser.getId()));
    }

    @PostMapping("/retry/{sourceDocumentId}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<KnowledgeNote> retry(@PathVariable Long sourceDocumentId,
                                             HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(distillationService.retryDistillation(sourceDocumentId, loginUser.getId()));
    }

    private String buildOutline(String goal, String preference) {
        String topic = cn.hutool.core.util.StrUtil.blankToDefault(goal, "该主题");
        try {
            String prompt = """
                    你是学习规划助手。请根据学习目标生成一份简洁的 Markdown 学习大纲（3到6个章节）。
                    不要输出候选网址。学习目标：%s
                    偏好：%s
                    """.formatted(topic, cn.hutool.core.util.StrUtil.blankToDefault(preference, "无"));
            return aiModelService.chat(prompt);
        } catch (Exception e) {
            return """
                    # %s 学习大纲（占位）

                    1. 基础概念与背景
                    2. 核心原理
                    3. 实践案例
                    4. 常见问题与进阶

                    > 未返回候选时请检查 search.provider 与对应 API Key，或手动粘贴 URL。
                    """.formatted(topic);
        }
    }

    private String mergeTags(KnowledgeIngestUrlRequest request) {
        if (request == null) {
            return null;
        }
        String tags = request.getTags();
        String agentQuery = request.getAgentQuery();
        if (cn.hutool.core.util.StrUtil.isBlank(agentQuery)) {
            return tags;
        }
        String marker = "agentQuery:" + agentQuery.trim();
        if (cn.hutool.core.util.StrUtil.isBlank(tags)) {
            return marker;
        }
        return tags.trim() + "," + marker;
    }

    private void requireSyncIngest() {
        if (readingRuntimeSettings.ingestAsync()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "暂不支持异步采集，请将 ingest.sync_mode 设为 sync");
        }
    }

    private KnowledgeSearchStrategy resolveSearchStrategy() {
        String provider = readingRuntimeSettings.searchProvider();
        if (provider == null || provider.isBlank()) {
            provider = "deepseek";
        }
        String normalized = provider.trim().toLowerCase();
        for (KnowledgeSearchStrategy strategy : knowledgeSearchStrategies) {
            if (normalized.equalsIgnoreCase(strategy.name())) {
                return strategy;
            }
        }
        throw new BusinessException(ErrorCode.OPERATION_ERROR,
                "未知搜索提供方: " + provider + "，当前支持 deepseek, tavily, placeholder");
    }
}
