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
import com.ai.model.dto.knowledge.KnowledgeNoteIndexRequest;
import com.ai.model.dto.knowledge.KnowledgeNotePublishRequest;
import com.ai.model.dto.knowledge.KnowledgeNoteQueryRequest;
import com.ai.model.dto.knowledge.KnowledgeNoteUpdateRequest;
import com.ai.model.dto.knowledge.KnowledgeProcessRequest;
import com.ai.model.dto.knowledge.KnowledgeSearchPreviewRequest;
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
import com.ai.setting.runtime.ReadingRuntimeSettings;
import com.mybatisflex.core.paginate.Page;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
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
        if (readingRuntimeSettings.ingestAsync()) {
            return ResultUtils.success(knowledgeReadingJobService.submit(request, loginUser.getId()));
        }
        return ResultUtils.success(knowledgeReadingJobService.runSync(request, loginUser.getId()));
    }

    @GetMapping("/reading-jobs/{jobId}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<KnowledgeReadingJobVO> getReadingJob(@PathVariable Long jobId,
                                                             HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(knowledgeReadingJobService.getJob(jobId, loginUser.getId()));
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

    @PostMapping("/search/preview")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<KnowledgeSearchPreviewVO> searchPreview(@RequestBody KnowledgeSearchPreviewRequest request,
                                                                HttpServletRequest httpRequest) {
        userService.getLoginUser(httpRequest);
        KnowledgeSearchPreviewVO vo = new KnowledgeSearchPreviewVO();
        String goal = request == null ? "" : request.getGoal();
        if (cn.hutool.core.util.StrUtil.isBlank(goal)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "学习目标 goal 不能为空");
        }
        String preference = request == null ? null : request.getPreference();
        vo.setGoal(goal);
        vo.setOutline(buildOutline(goal, preference));
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
