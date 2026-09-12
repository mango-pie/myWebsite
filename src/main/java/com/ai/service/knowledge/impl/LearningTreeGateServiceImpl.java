package com.ai.service.knowledge.impl;

import com.ai.config.ConditionalOnModule;

import com.ai.constant.AiUsageSceneConstant;
import com.ai.constant.OpsAuditActionConstant;
import com.ai.constant.knowledge.LearningTreeIntentConstant;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.mapper.knowledge.KnowledgeNoteMapper;
import com.ai.model.dto.knowledge.LearningGateRequest;
import com.ai.model.dto.knowledge.LearningLeafBatchSuggestRequest;
import com.ai.model.entity.knowledge.KnowledgeNote;
import com.ai.model.vo.knowledge.LearningBranchSummaryVO;
import com.ai.model.vo.knowledge.LearningBranchVO;
import com.ai.model.vo.knowledge.LearningGateVO;
import com.ai.model.vo.knowledge.LearningLeafBatchSuggestVO;
import com.ai.model.vo.knowledge.LearningLeafVO;
import com.ai.model.vo.knowledge.LearningReviewQuizVO;
import com.ai.model.vo.knowledge.LearningTreeVO;
import com.ai.ops.AiUsageCallContext;
import com.ai.service.OpsAuditLogService;
import com.ai.service.knowledge.KnowledgeAiModelService;
import com.ai.service.knowledge.LearningTreeGateService;
import com.ai.service.knowledge.LearningTreeService;
import com.ai.service.knowledge.learning.LearningGatePass;
import com.ai.service.knowledge.learning.LearningGatePassStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ConditionalOnModule("knowledge")
@Service
public class LearningTreeGateServiceImpl implements LearningTreeGateService {

    /** 低于该置信度前端应展示「可能相关」双选项，而非硬编码禁用 */
    public static final double LOW_CONFIDENCE_THRESHOLD = 0.6;
    private static final int REVIEW_NOTE_CHARS = 3000;
    private static final int QUIZ_QUESTIONS = 5;

    private static final Pattern JSON_BLOCK = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    @Resource
    private LearningTreeService learningTreeService;
    @Resource
    private LearningGatePassStore learningGatePassStore;
    @Resource
    private KnowledgeAiModelService aiModelService;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private KnowledgeNoteMapper knowledgeNoteMapper;
    @Resource
    private OpsAuditLogService opsAuditLogService;

    @Override
    public LearningGateVO gate(LearningGateRequest request, Long userId) {
        if (request == null || request.getDomainId() == null || StrUtil.isBlank(request.getQuestion())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "domainId 与 question 不能为空");
        }
        String goal = request.getQuestion().trim();

        // 带理由跳过：人工 override，不再调用 AI
        if (StrUtil.isNotBlank(request.getOverrideIntent())) {
            return issueOverride(request, goal, userId);
        }

        // 判定结果缓存命中：同一 domain+question 不重复烧模型
        LearningGatePassStore.Judgment cached = learningGatePassStore.getCachedJudgment(request.getDomainId(), goal);
        if (cached != null) {
            return issuePassFromJudgment(request.getDomainId(), goal, cached, userId);
        }

        LearningGateVO vo = judge(request, goal, userId);
        learningGatePassStore.cacheJudgment(request.getDomainId(), goal, toJudgment(request.getDomainId(), goal, vo));
        return vo;
    }

    private LearningGateVO judge(LearningGateRequest request, String goal, Long userId) {
        LearningTreeVO tree = learningTreeService.getTree(request.getDomainId(), userId, false);
        String snapshotText = formatSnapshot(tree);
        String prompt = """
                你是学习领域门闩助手。仅输出一个 JSON 对象，不要其它文字。
                字段：related(boolean), intent("CHAT_ONLY"|"BRANCH"|"LEAF"), reason(string),
                confidence(number, 0到1，你对判定的把握度),
                answer(string, related=false时必填), hints(string数组, 可选),
                suggestedBranchTitle(string, 可选), suggestedParentBranchId(number, 可选, 0表示L1)
                规则：related=false 时 intent 必须为 CHAT_ONLY 并给出 answer 与 hints(引导用户在当前领域下可问什么)。
                related=true 时 intent=BRANCH 表示只需建主题目录不出文章；intent=LEAF 表示应搜文合蒸一篇。
                当前领域：%s
                已有主题枝（id parentId depth title leafCount）：
                %s
                用户问题：%s
                """.formatted(tree.getDomain().getName(), snapshotText, goal);

        AiUsageCallContext.set(userId, AiUsageSceneConstant.TREE_GATE, request.getDomainId(), truncate(goal, 120));
        String raw;
        try {
            raw = aiModelService.chat(prompt);
        } finally {
            AiUsageCallContext.clear();
        }
        JsonNode json = parseJson(raw);
        LearningGateVO vo = new LearningGateVO();
        vo.setRelated(json.path("related").asBoolean(false));
        vo.setIntent(normalizeIntent(json.path("intent").asText("CHAT_ONLY"), vo.isRelated()));
        vo.setReason(json.path("reason").asText(null));
        vo.setConfidence(parseConfidence(json));
        if (json.has("answer") && !json.get("answer").isNull()) {
            vo.setAnswer(json.get("answer").asText());
        }
        if (json.has("hints") && json.get("hints").isArray()) {
            List<String> hints = new ArrayList<>();
            json.get("hints").forEach(n -> hints.add(n.asText()));
            vo.setHints(hints);
        }
        resolveSuggestedBranch(vo, json, tree);
        auditGate(userId, request.getDomainId(), goal, vo, false);
        if (vo.isRelated()) {
            vo.setGatePassId(issuePass(request.getDomainId(), goal, vo, userId, false, null));
        }
        return vo;
    }

    private LearningGateVO issueOverride(LearningGateRequest request, String goal, Long userId) {
        String intent = normalizeIntent(request.getOverrideIntent(), true);
        if (StrUtil.isBlank(request.getOverrideReason())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "带理由跳过必须提供 overrideReason");
        }
        if (intent.equals(LearningTreeIntentConstant.CHAT_ONLY)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "带理由跳过仅支持 LEAF / BRANCH 意图");
        }
        learningTreeService.requireDomain(request.getDomainId(), userId);
        LearningGateVO vo = new LearningGateVO();
        vo.setRelated(true);
        vo.setIntent(intent);
        vo.setReason("人工覆盖 AI 判定：" + request.getOverrideReason().trim());
        vo.setConfidence(1.0);
        vo.setOverridden(true);
        vo.setGatePassId(issuePass(request.getDomainId(), goal, vo, userId, true, request.getOverrideReason().trim()));
        auditGate(userId, request.getDomainId(), goal, vo, true);
        return vo;
    }

    private String issuePass(Long domainId, String goal, LearningGateVO vo, Long userId,
                             boolean overridden, String overrideReason) {
        LearningGatePass pass = LearningGatePass.builder()
                .userId(userId)
                .domainId(domainId)
                .related(true)
                .intent(vo.getIntent())
                .confidence(vo.getConfidence())
                .overridden(overridden)
                .overrideReason(overrideReason)
                .goal(goal)
                .suggestedBranchId(vo.getSuggestedBranchId())
                .suggestedBranchTitle(vo.getSuggestedBranchTitle())
                .suggestedParentBranchId(vo.getSuggestedParentBranchId())
                .build();
        return learningGatePassStore.issue(pass);
    }

    private LearningGateVO issuePassFromJudgment(Long domainId, String goal,
                                                 LearningGatePassStore.Judgment judgment, Long userId) {
        LearningGateVO vo = new LearningGateVO();
        vo.setRelated(judgment.related());
        vo.setIntent(judgment.intent());
        vo.setReason(judgment.reason());
        vo.setConfidence(judgment.confidence());
        vo.setAnswer(judgment.answer());
        vo.setHints(judgment.hints());
        vo.setSuggestedBranchId(judgment.suggestedBranchId());
        vo.setSuggestedBranchTitle(judgment.suggestedBranchTitle());
        vo.setSuggestedParentBranchId(judgment.suggestedParentBranchId());
        if (vo.isRelated()) {
            vo.setGatePassId(issuePass(domainId, goal, vo, userId, false, null));
        }
        return vo;
    }

    private LearningGatePassStore.Judgment toJudgment(Long domainId, String goal, LearningGateVO vo) {
        return new LearningGatePassStore.Judgment(
                domainId, goal, vo.isRelated(), vo.getIntent(), vo.getReason(),
                vo.getAnswer(), vo.getHints(), vo.getConfidence(),
                vo.getSuggestedBranchId(), vo.getSuggestedBranchTitle(), vo.getSuggestedParentBranchId());
    }

    private Double parseConfidence(JsonNode json) {
        JsonNode node = json.path("confidence");
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        double v = node.asDouble(0.5);
        return Math.max(0.0, Math.min(1.0, v));
    }

    private void auditGate(Long userId, Long domainId, String goal, LearningGateVO vo, boolean overridden) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("domainId", String.valueOf(domainId));
        detail.put("goal", truncate(goal, 120));
        detail.put("related", String.valueOf(vo.isRelated()));
        detail.put("intent", vo.getIntent());
        detail.put("confidence", vo.getConfidence() == null ? "null" : vo.getConfidence().toString());
        detail.put("overridden", String.valueOf(overridden));
        opsAuditLogService.audit(
                OpsAuditActionConstant.LEARNING_GATE,
                userId,
                OpsAuditActionConstant.RESOURCE_LEARNING_DOMAIN,
                String.valueOf(domainId),
                true,
                detail,
                null);
    }

    @Override
    public void reportGateMistake(String gatePassId, String reason, Long userId) {
        if (StrUtil.isBlank(gatePassId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "gatePassId 不能为空");
        }
        LearningGatePass pass = learningGatePassStore.get(gatePassId);
        if (pass == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "门闩已失效，无法回写");
        }
        if (!pass.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "门闩与用户不匹配");
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("domainId", String.valueOf(pass.getDomainId()));
        detail.put("goal", truncate(pass.getGoal(), 120));
        detail.put("intent", pass.getIntent());
        detail.put("overridden", String.valueOf(pass.isOverridden()));
        detail.put("reason", truncate(StrUtil.blankToDefault(reason, "未填写"), 200));
        opsAuditLogService.audit(
                OpsAuditActionConstant.LEARNING_GATE_MISTAKE,
                userId,
                OpsAuditActionConstant.RESOURCE_LEARNING_DOMAIN,
                String.valueOf(pass.getDomainId()),
                true,
                detail,
                null);
        // 失效判定缓存：下次同一问题重新判定
        learningGatePassStore.clearJudgment(pass.getDomainId(), pass.getGoal());
    }

    @Override
    public LearningReviewQuizVO reviewQuiz(Long branchId, Long userId) {
        if (branchId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "branchId 不能为空");
        }
        LearningBranchVO branch = learningTreeService.requireBranch(branchId, userId);
        List<LearningLeafVO> leaves = learningTreeService.listLeavesByBranch(branchId, userId);
        StringBuilder notesBlock = new StringBuilder();
        for (LearningLeafVO leaf : leaves) {
            KnowledgeNote note = knowledgeNoteMapper.selectOneById(leaf.getNoteId());
            if (note == null || StrUtil.isBlank(note.getDistilledMd())) {
                continue;
            }
            notesBlock.append("- ").append(leaf.getNoteTitle()).append(":\n")
                    .append(truncate(cleanMarkdown(note.getDistilledMd()), REVIEW_NOTE_CHARS)).append("\n\n");
        }
        LearningReviewQuizVO vo = new LearningReviewQuizVO();
        vo.setBranchId(branchId);
        vo.setBranchTitle(branch.getTitle());
        if (notesBlock.length() == 0) {
            vo.setQuestions(List.of());
            return vo;
        }
        String prompt = """
                你是学习复习助手。基于以下精读笔记内容，抽取 %d 道自测题，考察关键知识点与代码精髓，难度递进。
                仅输出 JSON：{"questions":[{"question":"...","answer":"..."}]}
                答案要具体可核对，不要空话。
                笔记内容：
                %s
                """.formatted(QUIZ_QUESTIONS, notesBlock);
        AiUsageCallContext.set(userId, AiUsageSceneConstant.REVIEW_QUIZ, branchId, "branch-review-quiz");
        String raw;
        try {
            raw = aiModelService.chat(prompt);
        } finally {
            AiUsageCallContext.clear();
        }
        try {
            JsonNode root = parseJson(raw);
            JsonNode arr = root.path("questions");
            if (arr.isArray()) {
                for (JsonNode node : arr) {
                    LearningReviewQuizVO.Question q = new LearningReviewQuizVO.Question();
                    q.setQuestion(node.path("question").asText(""));
                    q.setAnswer(node.path("answer").asText(""));
                    if (StrUtil.isNotBlank(q.getQuestion())) {
                        vo.getQuestions().add(q);
                    }
                }
            }
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "复习抽题 AI 响应解析失败：" + e.getMessage());
        }
        return vo;
    }

    @Override
    public LearningBranchSummaryVO branchSummary(Long branchId, Long userId) {
        if (branchId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "branchId 不能为空");
        }
        LearningBranchVO branch = learningTreeService.requireBranch(branchId, userId);
        List<LearningLeafVO> leaves = learningTreeService.listLeavesByBranch(branchId, userId);
        StringBuilder notesBlock = new StringBuilder();
        for (LearningLeafVO leaf : leaves) {
            KnowledgeNote note = knowledgeNoteMapper.selectOneById(leaf.getNoteId());
            if (note == null || StrUtil.isBlank(note.getDistilledMd())) {
                continue;
            }
            notesBlock.append("## ").append(leaf.getNoteTitle()).append("\n")
                    .append(truncate(cleanMarkdown(note.getDistilledMd()), REVIEW_NOTE_CHARS)).append("\n\n");
        }
        LearningBranchSummaryVO vo = new LearningBranchSummaryVO();
        vo.setBranchId(branchId);
        vo.setBranchTitle(branch.getTitle());
        if (notesBlock.length() == 0) {
            vo.setSummaryMd("该枝下暂无已掌握的精读笔记，先去合蒸几篇再来生成综述吧。");
            return vo;
        }
        String prompt = """
                你是学习知识地图助手。基于「%s」枝下的精读笔记，生成一份整枝知识地图 / 综述 Markdown：
                1. 一句话主线（该枝学了什么）
                2. 核心概念与要点（按主题分组，附对应笔记标题）
                3. 代码 / 配置精髓速查
                4. 易错点与延伸问题
                5. 该枝的薄弱缺口（笔记中未覆盖但该主题常见的内容）
                只使用笔记中出现的内容，不要编造。
                笔记内容：
                %s
                """.formatted(branch.getTitle(), notesBlock);
        AiUsageCallContext.set(userId, AiUsageSceneConstant.BRANCH_SUMMARY, branchId, "branch-summary");
        String summary;
        try {
            summary = aiModelService.chat(prompt);
        } finally {
            AiUsageCallContext.clear();
        }
        vo.setSummaryMd(StrUtil.isBlank(summary) ? "生成失败，请重试。" : summary.trim());
        return vo;
    }

    @Override
    public LearningLeafBatchSuggestVO batchSuggest(LearningLeafBatchSuggestRequest request, Long userId) {
        if (request == null || request.getDomainId() == null || request.getNotes() == null || request.getNotes().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "domainId 与 notes 不能为空");
        }
        LearningTreeVO tree = learningTreeService.getTree(request.getDomainId(), userId, false);
        StringBuilder notesBlock = new StringBuilder();
        for (LearningLeafBatchSuggestRequest.NoteBrief n : request.getNotes()) {
            notesBlock.append("- noteId=").append(n.getNoteId())
                    .append(" title=").append(StrUtil.blankToDefault(n.getTitle(), ""))
                    .append(" summary=").append(StrUtil.blankToDefault(n.getSummary(), ""))
                    .append("\n");
        }
        String prompt = """
                你是挂枝助手。根据领域与已有枝，为每条 note 建议挂到哪条枝。仅输出 JSON：{"suggestions":[{"noteId":1,"branchId":null,"newBranchTitle":"集合","parentBranchId":0,"reason":"..."}]}
                branchId 优先用已有枝 id；若无合适枝则 newBranchTitle+parentBranchId(0=L1)。
                领域：%s
                枝列表：
                %s
                待挂 note：
                %s
                """.formatted(tree.getDomain().getName(), formatSnapshot(tree), notesBlock);

        AiUsageCallContext.set(userId, AiUsageSceneConstant.TREE_REATTACH, request.getDomainId(), "batch-suggest");
        String raw;
        try {
            raw = aiModelService.chat(prompt);
        } finally {
            AiUsageCallContext.clear();
        }
        JsonNode root = parseJson(raw);
        LearningLeafBatchSuggestVO vo = new LearningLeafBatchSuggestVO();
        List<LearningLeafBatchSuggestVO.Suggestion> list = new ArrayList<>();
        JsonNode arr = root.path("suggestions");
        if (arr.isArray()) {
            for (JsonNode node : arr) {
                LearningLeafBatchSuggestVO.Suggestion s = new LearningLeafBatchSuggestVO.Suggestion();
                s.setNoteId(node.path("noteId").asLong(0));
                if (node.has("branchId") && !node.get("branchId").isNull()) {
                    s.setBranchId(node.get("branchId").asLong());
                }
                if (node.has("newBranchTitle")) {
                    s.setNewBranchTitle(node.get("newBranchTitle").asText(null));
                }
                if (node.has("parentBranchId")) {
                    s.setParentBranchId(node.get("parentBranchId").asLong(0));
                }
                s.setReason(node.path("reason").asText(null));
                list.add(s);
            }
        }
        vo.setSuggestions(list);
        return vo;
    }

    @Override
    public void validateTreeModeV2Access(Long userId, Long domainId, String gatePassId, Long branchId, Boolean skipGate) {
        if (domainId == null) {
            return;
        }
        if (Boolean.TRUE.equals(skipGate)) {
            if (branchId == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空枝补学须传 branchId");
            }
            learningTreeService.validateEmptyBranchLearnSkipGate(branchId, domainId, userId);
            return;
        }
        if (StrUtil.isBlank(gatePassId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "树模式须先门闩或传 gatePassId");
        }
        learningGatePassStore.validateForV2(userId, domainId, gatePassId, true);
    }

    private void resolveSuggestedBranch(LearningGateVO vo, JsonNode json, LearningTreeVO tree) {
        if (json.has("suggestedParentBranchId") && !json.get("suggestedParentBranchId").isNull()) {
            vo.setSuggestedParentBranchId(json.get("suggestedParentBranchId").asLong(0));
        }
        String title = json.has("suggestedBranchTitle") ? json.get("suggestedBranchTitle").asText(null) : null;
        vo.setSuggestedBranchTitle(title);
        if (StrUtil.isNotBlank(title) && tree.getBranches() != null) {
            for (LearningBranchVO b : tree.getBranches()) {
                if (title.trim().equalsIgnoreCase(b.getTitle())) {
                    vo.setSuggestedBranchId(b.getId());
                    break;
                }
            }
        }
    }

    private String formatSnapshot(LearningTreeVO tree) {
        if (tree.getBranches() == null || tree.getBranches().isEmpty()) {
            return "(无)";
        }
        StringBuilder sb = new StringBuilder();
        for (LearningBranchVO b : tree.getBranches()) {
            sb.append(b.getId()).append(' ')
                    .append(b.getParentBranchId() == null ? 0 : b.getParentBranchId()).append(' ')
                    .append(b.getDepth()).append(' ')
                    .append(b.getTitle()).append(' ')
                    .append(b.getLeafCount()).append('\n');
        }
        return sb.toString();
    }

    private JsonNode parseJson(String raw) {
        if (StrUtil.isBlank(raw)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "门闩 AI 响应为空");
        }
        String text = raw.trim();
        Matcher m = JSON_BLOCK.matcher(text);
        if (m.find()) {
            text = m.group(1).trim();
        }
        try {
            return objectMapper.readTree(text);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "门闩 AI 响应非合法 JSON");
        }
    }

    private String normalizeIntent(String intent, boolean related) {
        if (!related) {
            return LearningTreeIntentConstant.CHAT_ONLY;
        }
        String upper = intent == null ? "" : intent.trim().toUpperCase(Locale.ROOT);
        if (LearningTreeIntentConstant.LEAF.equals(upper) || LearningTreeIntentConstant.BRANCH.equals(upper)) {
            return upper;
        }
        return LearningTreeIntentConstant.LEAF;
    }

    private static String cleanMarkdown(String md) {
        if (md == null) {
            return "";
        }
        return md.replaceAll("\\[src\\d+\\]", "");
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
