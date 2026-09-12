package com.ai.service.knowledge;

import com.ai.model.dto.knowledge.LearningGateRequest;
import com.ai.model.dto.knowledge.LearningLeafBatchSuggestRequest;
import com.ai.model.vo.knowledge.LearningBranchSummaryVO;
import com.ai.model.vo.knowledge.LearningGateVO;
import com.ai.model.vo.knowledge.LearningLeafBatchSuggestVO;
import com.ai.model.vo.knowledge.LearningReviewQuizVO;

public interface LearningTreeGateService {

    LearningGateVO gate(LearningGateRequest request, Long userId);

    LearningLeafBatchSuggestVO batchSuggest(LearningLeafBatchSuggestRequest request, Long userId);

    void validateTreeModeV2Access(Long userId, Long domainId, String gatePassId, Long branchId, Boolean skipGate);

    /** 用户标记门闩判定错误：回写审计 + 失效判定缓存（数据闭环） */
    void reportGateMistake(String gatePassId, String reason, Long userId);

    /** 复习模式：按枝抽自测题（基于枝下 note 的蒸馏结构） */
    LearningReviewQuizVO reviewQuiz(Long branchId, Long userId);

    /** 枝级综述：聚合枝下多篇 note 生成知识地图 */
    LearningBranchSummaryVO branchSummary(Long branchId, Long userId);
}
