package com.ai.controller;

import com.ai.annotation.AuthCheck;
import com.ai.common.BaseResponse;
import com.ai.common.ResultUtils;
import com.ai.config.ConditionalOnModule;
import com.ai.constant.UserConstant;
import com.ai.model.dto.knowledge.LearningBranchCreateRequest;
import com.ai.model.dto.knowledge.LearningBranchMergeRequest;
import com.ai.model.dto.knowledge.LearningBranchUpdateRequest;
import com.ai.model.dto.knowledge.LearningDomainCreateRequest;
import com.ai.model.dto.knowledge.LearningDomainUpdateRequest;
import com.ai.model.dto.knowledge.LearningGateMistakeRequest;
import com.ai.model.dto.knowledge.LearningGateRequest;
import com.ai.model.dto.knowledge.LearningLeafAttachRequest;
import com.ai.model.dto.knowledge.LearningLeafBatchSuggestRequest;
import com.ai.model.dto.knowledge.LearningLeafMoveRequest;
import com.ai.model.entity.User;
import com.ai.model.vo.knowledge.KnowledgeNoteVO;
import com.ai.model.vo.knowledge.LearningBranchSummaryVO;
import com.ai.model.vo.knowledge.LearningBranchVO;
import com.ai.model.vo.knowledge.LearningDomainVO;
import com.ai.model.vo.knowledge.LearningGateVO;
import com.ai.model.vo.knowledge.LearningLeafBatchSuggestVO;
import com.ai.model.vo.knowledge.LearningLeafVO;
import com.ai.model.vo.knowledge.LearningReviewQuizVO;
import com.ai.model.vo.knowledge.LearningTreeVO;
import com.ai.service.UserService;
import com.ai.service.knowledge.LearningTreeGateService;
import com.ai.service.knowledge.LearningTreeService;
import com.ai.service.knowledge.support.KnowledgeMaterialCacheStore;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@ConditionalOnModule("knowledge")
@RestController
@RequestMapping("/admin/knowledge/learning")
public class LearningAdminController {

    @Resource
    private UserService userService;
    @Resource
    private LearningTreeService learningTreeService;
    @Resource
    private LearningTreeGateService learningTreeGateService;

    @Resource
    private KnowledgeMaterialCacheStore knowledgeMaterialCacheStore;

    @GetMapping("/domains")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<List<LearningDomainVO>> listDomains(HttpServletRequest httpRequest) {
        User user = userService.getLoginUser(httpRequest);
        return ResultUtils.success(learningTreeService.listDomains(user.getId()));
    }

    @PostMapping("/domains")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<LearningDomainVO> createDomain(@RequestBody LearningDomainCreateRequest request,
                                                       HttpServletRequest httpRequest) {
        User user = userService.getLoginUser(httpRequest);
        return ResultUtils.success(learningTreeService.createDomain(request, user.getId()));
    }

    @PutMapping("/domains/{id}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<LearningDomainVO> updateDomain(@PathVariable Long id,
                                                       @RequestBody LearningDomainUpdateRequest request,
                                                       HttpServletRequest httpRequest) {
        User user = userService.getLoginUser(httpRequest);
        return ResultUtils.success(learningTreeService.updateDomain(id, request, user.getId()));
    }

    @DeleteMapping("/domains/{id}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteDomain(@PathVariable Long id, HttpServletRequest httpRequest) {
        User user = userService.getLoginUser(httpRequest);
        learningTreeService.deleteDomain(id, user.getId());
        return ResultUtils.success(true);
    }

    @GetMapping("/domains/{domainId}/tree")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<LearningTreeVO> getTree(@PathVariable Long domainId,
                                                @RequestParam(defaultValue = "false") boolean includeLeaves,
                                                HttpServletRequest httpRequest) {
        User user = userService.getLoginUser(httpRequest);
        return ResultUtils.success(learningTreeService.getTree(domainId, user.getId(), includeLeaves));
    }

    @PostMapping("/domains/{domainId}/branches")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<LearningBranchVO> createBranch(@PathVariable Long domainId,
                                                       @RequestBody LearningBranchCreateRequest request,
                                                       HttpServletRequest httpRequest) {
        User user = userService.getLoginUser(httpRequest);
        return ResultUtils.success(learningTreeService.createBranch(domainId, request, user.getId()));
    }

    @PutMapping("/branches/{id}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<LearningBranchVO> updateBranch(@PathVariable Long id,
                                                       @RequestBody LearningBranchUpdateRequest request,
                                                       HttpServletRequest httpRequest) {
        User user = userService.getLoginUser(httpRequest);
        return ResultUtils.success(learningTreeService.updateBranch(id, request, user.getId()));
    }

    @DeleteMapping("/branches/{id}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteBranch(@PathVariable Long id, HttpServletRequest httpRequest) {
        User user = userService.getLoginUser(httpRequest);
        learningTreeService.deleteBranch(id, user.getId());
        return ResultUtils.success(true);
    }

    /** 强制刷新材料缓存：清空 learning:material:*（前端采集页「强制刷新缓存」入口） */
    @DeleteMapping("/material-cache")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Integer> clearMaterialCache(HttpServletRequest httpRequest) {
        User user = userService.getLoginUser(httpRequest);
        return ResultUtils.success(knowledgeMaterialCacheStore.clearAll());
    }

    @PostMapping("/branches/{sourceId}/merge")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> mergeBranch(@PathVariable Long sourceId,
                                             @RequestBody LearningBranchMergeRequest request,
                                             HttpServletRequest httpRequest) {
        User user = userService.getLoginUser(httpRequest);
        learningTreeService.mergeBranch(sourceId, request, user.getId());
        return ResultUtils.success(true);
    }

    @PostMapping("/gate")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<LearningGateVO> gate(@RequestBody LearningGateRequest request,
                                             HttpServletRequest httpRequest) {
        User user = userService.getLoginUser(httpRequest);
        return ResultUtils.success(learningTreeGateService.gate(request, user.getId()));
    }

    /** 用户标记门闩判定错误：回写审计 + 失效判定缓存 */
    @PostMapping("/gate/{gatePassId}/report-mistake")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> reportGateMistake(@PathVariable String gatePassId,
                                                   @RequestBody(required = false) LearningGateMistakeRequest request,
                                                   HttpServletRequest httpRequest) {
        User user = userService.getLoginUser(httpRequest);
        learningTreeGateService.reportGateMistake(gatePassId,
                request == null ? null : request.getReason(), user.getId());
        return ResultUtils.success(true);
    }

    /** 复习模式：按枝抽自测题（答案折叠） */
    @GetMapping("/branches/{branchId}/review-quiz")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<LearningReviewQuizVO> reviewQuiz(@PathVariable Long branchId,
                                                         HttpServletRequest httpRequest) {
        User user = userService.getLoginUser(httpRequest);
        return ResultUtils.success(learningTreeGateService.reviewQuiz(branchId, user.getId()));
    }

    /** 枝级综述：聚合枝下多篇 note 生成知识地图 */
    @PostMapping("/branches/{branchId}/summary")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<LearningBranchSummaryVO> branchSummary(@PathVariable Long branchId,
                                                               HttpServletRequest httpRequest) {
        User user = userService.getLoginUser(httpRequest);
        return ResultUtils.success(learningTreeGateService.branchSummary(branchId, user.getId()));
    }

    @PostMapping("/leaves/attach")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<LearningLeafVO> attachLeaf(@RequestBody LearningLeafAttachRequest request,
                                                   HttpServletRequest httpRequest) {
        User user = userService.getLoginUser(httpRequest);
        return ResultUtils.success(learningTreeService.attachLeaf(request, user.getId()));
    }

    @PutMapping("/leaves/move")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<LearningLeafVO> moveLeaf(@RequestBody LearningLeafMoveRequest request,
                                                 HttpServletRequest httpRequest) {
        User user = userService.getLoginUser(httpRequest);
        return ResultUtils.success(learningTreeService.moveLeaf(request, user.getId()));
    }

    @DeleteMapping("/leaves/by-note/{noteId}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> detachLeaf(@PathVariable Long noteId, HttpServletRequest httpRequest) {
        User user = userService.getLoginUser(httpRequest);
        learningTreeService.detachLeafByNote(noteId, user.getId());
        return ResultUtils.success(true);
    }

    @GetMapping("/branches/{branchId}/leaves")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<List<LearningLeafVO>> listLeavesByBranch(@PathVariable Long branchId,
                                                                 HttpServletRequest httpRequest) {
        User user = userService.getLoginUser(httpRequest);
        return ResultUtils.success(learningTreeService.listLeavesByBranch(branchId, user.getId()));
    }

    @GetMapping("/domains/{domainId}/unattached-notes")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<List<KnowledgeNoteVO>> unattachedNotes(@PathVariable Long domainId,
                                                              @RequestParam(defaultValue = "1") int pageNum,
                                                              @RequestParam(defaultValue = "20") int pageSize,
                                                              HttpServletRequest httpRequest) {
        User user = userService.getLoginUser(httpRequest);
        learningTreeService.requireDomain(domainId, user.getId());
        return ResultUtils.success(learningTreeService.listUnattachedNotes(user.getId(), pageNum, pageSize));
    }

    @PostMapping("/leaves/batch-suggest")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<LearningLeafBatchSuggestVO> batchSuggest(@RequestBody LearningLeafBatchSuggestRequest request,
                                                                 HttpServletRequest httpRequest) {
        User user = userService.getLoginUser(httpRequest);
        return ResultUtils.success(learningTreeGateService.batchSuggest(request, user.getId()));
    }
}
