package com.ai.service.knowledge;

import com.ai.model.dto.knowledge.LearningBranchCreateRequest;
import com.ai.model.dto.knowledge.LearningBranchMergeRequest;
import com.ai.model.dto.knowledge.LearningBranchUpdateRequest;
import com.ai.model.dto.knowledge.LearningDomainCreateRequest;
import com.ai.model.dto.knowledge.LearningDomainUpdateRequest;
import com.ai.model.dto.knowledge.LearningLeafAttachRequest;
import com.ai.model.dto.knowledge.LearningLeafMoveRequest;
import com.ai.model.vo.knowledge.LearningBranchVO;
import com.ai.model.vo.knowledge.LearningDomainVO;
import com.ai.model.vo.knowledge.LearningLeafVO;
import com.ai.model.vo.knowledge.LearningTreeVO;
import com.ai.model.vo.knowledge.KnowledgeNoteVO;

import java.util.List;

public interface LearningTreeService {

    List<LearningDomainVO> listDomains(Long userId);

    LearningDomainVO createDomain(LearningDomainCreateRequest request, Long userId);

    LearningDomainVO updateDomain(Long domainId, LearningDomainUpdateRequest request, Long userId);

    void deleteDomain(Long domainId, Long userId);

    LearningTreeVO getTree(Long domainId, Long userId, boolean includeLeaves);

    LearningBranchVO createBranch(Long domainId, LearningBranchCreateRequest request, Long userId);

    LearningBranchVO updateBranch(Long branchId, LearningBranchUpdateRequest request, Long userId);

    void deleteBranch(Long branchId, Long userId);

    void mergeBranch(Long sourceBranchId, LearningBranchMergeRequest request, Long userId);

    LearningLeafVO attachLeaf(LearningLeafAttachRequest request, Long userId);

    LearningLeafVO moveLeaf(LearningLeafMoveRequest request, Long userId);

    void detachLeafByNote(Long noteId, Long userId);

    List<LearningLeafVO> listLeavesByBranch(Long branchId, Long userId);

    List<KnowledgeNoteVO> listUnattachedNotes(Long userId, int pageNum, int pageSize);

    LearningDomainVO requireDomain(Long domainId, Long userId);

    LearningBranchVO requireBranch(Long branchId, Long userId);

    void validateEmptyBranchLearnSkipGate(Long branchId, Long domainId, Long userId);
}
