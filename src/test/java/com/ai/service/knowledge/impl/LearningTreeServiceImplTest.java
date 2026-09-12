package com.ai.service.knowledge.impl;

import com.ai.exception.BusinessException;
import com.ai.mapper.knowledge.KnowledgeNoteMapper;
import com.ai.mapper.knowledge.LearningBranchMapper;
import com.ai.mapper.knowledge.LearningDomainMapper;
import com.ai.mapper.knowledge.LearningLeafMapper;
import com.ai.mapper.knowledge.SourceDocumentMapper;
import com.ai.model.dto.knowledge.LearningBranchMergeRequest;
import com.ai.model.dto.knowledge.LearningDomainCreateRequest;
import com.ai.model.dto.knowledge.LearningLeafAttachRequest;
import com.ai.model.entity.knowledge.KnowledgeNote;
import com.ai.model.entity.knowledge.LearningBranch;
import com.ai.model.entity.knowledge.LearningDomain;
import com.ai.model.entity.knowledge.LearningLeaf;
import com.ai.model.entity.knowledge.SourceDocument;
import com.ai.model.vo.knowledge.LearningLeafVO;
import com.ai.service.OpsAuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningTreeServiceImplTest {

    @Mock
    private LearningDomainMapper learningDomainMapper;
    @Mock
    private LearningBranchMapper learningBranchMapper;
    @Mock
    private LearningLeafMapper learningLeafMapper;
    @Mock
    private KnowledgeNoteMapper knowledgeNoteMapper;
    @Mock
    private SourceDocumentMapper sourceDocumentMapper;

    @Mock
    private OpsAuditLogService opsAuditLogService;

    @InjectMocks
    private LearningTreeServiceImpl learningTreeService;

    @Test
    void deleteBranch_rejectsWhenHasLeaves() {
        LearningBranch branch = new LearningBranch();
        branch.setId(100L);
        branch.setUserId(1L);
        branch.setDomainId(10L);
        when(learningBranchMapper.selectOneById(100L)).thenReturn(branch);
        when(learningLeafMapper.selectCountByQuery(any())).thenReturn(1L);

        assertThrows(BusinessException.class, () -> learningTreeService.deleteBranch(100L, 1L));
    }

    @Test
    void createDomain_rejectsDuplicateName() {
        LearningDomain existing = new LearningDomain();
        existing.setId(5L);
        existing.setUserId(1L);
        existing.setName("Java");
        when(learningDomainMapper.selectOneByQuery(any())).thenReturn(existing);

        LearningDomainCreateRequest req = new LearningDomainCreateRequest();
        req.setName("Java");

        assertThrows(BusinessException.class, () -> learningTreeService.createDomain(req, 1L));
    }

    @Test
    void attachLeaf_revivesLogicDeletedRow() {
        LearningDomain domain = domain(10L, 1L);
        LearningBranch branch = branch(100L, 10L, 1L);
        KnowledgeNote note = note(9001L, 50L);
        SourceDocument source = source(50L, 1L);

        LearningLeaf deleted = new LearningLeaf();
        deleted.setId(700L);
        deleted.setKnowledgeNoteId(9001L);
        deleted.setIsDelete(1);

        when(learningDomainMapper.selectOneById(10L)).thenReturn(domain);
        when(knowledgeNoteMapper.selectOneById(9001L)).thenReturn(note);
        when(sourceDocumentMapper.selectOneById(50L)).thenReturn(source);
        when(learningBranchMapper.selectOneById(100L)).thenReturn(branch);
        when(learningLeafMapper.selectOneByQuery(any())).thenReturn(deleted);

        LearningLeafAttachRequest req = new LearningLeafAttachRequest();
        req.setDomainId(10L);
        req.setNoteId(9001L);
        req.setBranchId(100L);

        LearningLeafVO vo = learningTreeService.attachLeaf(req, 1L);

        assertEquals(700L, vo.getId());
        assertEquals(0, deleted.getIsDelete());
        assertEquals(100L, deleted.getBranchId());
        verify(learningLeafMapper).update(deleted);
        verify(learningLeafMapper, never()).insert(any());
    }

    @Test
    void deleteDomain_cascadesBranchesAndLeaves() {
        LearningDomain domain = domain(10L, 1L);
        LearningBranch b1 = branch(100L, 10L, 1L);
        LearningBranch b2 = branch(101L, 10L, 1L);
        when(learningDomainMapper.selectOneById(10L)).thenReturn(domain);
        when(learningBranchMapper.selectListByQuery(any())).thenReturn(List.of(b1, b2));

        learningTreeService.deleteDomain(10L, 1L);

        verify(learningLeafMapper, org.mockito.Mockito.atLeast(2)).deleteByQuery(any());
        verify(learningBranchMapper).deleteById(100L);
        verify(learningBranchMapper).deleteById(101L);
        verify(learningDomainMapper).deleteById(10L);
    }

    @Test
    void mergeBranch_movesLeavesThenDeletesSource() {
        LearningBranch source = branch(100L, 10L, 1L);
        LearningBranch target = branch(200L, 10L, 1L);
        LearningLeaf leaf = new LearningLeaf();
        leaf.setId(1L);
        leaf.setBranchId(100L);

        when(learningBranchMapper.selectOneById(100L)).thenReturn(source);
        when(learningBranchMapper.selectOneById(200L)).thenReturn(target);
        when(learningLeafMapper.selectListByQuery(any())).thenReturn(List.of(leaf));
        when(learningBranchMapper.selectListByQuery(any())).thenReturn(List.of());

        LearningBranchMergeRequest req = new LearningBranchMergeRequest();
        req.setTargetBranchId(200L);
        learningTreeService.mergeBranch(100L, req, 1L);

        assertEquals(200L, leaf.getBranchId());
        verify(learningLeafMapper).update(leaf);
        verify(learningBranchMapper).deleteById(100L);
    }

    @Test
    void mergeBranch_rejectsWhenSourceHasChildren() {
        LearningBranch source = branch(100L, 10L, 1L);
        LearningBranch target = branch(200L, 10L, 1L);
        LearningBranch child = branch(101L, 10L, 1L);
        child.setParentBranchId(100L);

        when(learningBranchMapper.selectOneById(100L)).thenReturn(source);
        when(learningBranchMapper.selectOneById(200L)).thenReturn(target);
        when(learningLeafMapper.selectListByQuery(any())).thenReturn(List.of());
        when(learningBranchMapper.selectListByQuery(any())).thenReturn(List.of(child));

        LearningBranchMergeRequest req = new LearningBranchMergeRequest();
        req.setTargetBranchId(200L);
        assertThrows(BusinessException.class, () -> learningTreeService.mergeBranch(100L, req, 1L));
    }

    @Test
    void validateEmptyBranchLearnSkipGate_rejectsWhenHasLeaves() {
        LearningBranch branch = branch(100L, 10L, 1L);
        when(learningBranchMapper.selectOneById(100L)).thenReturn(branch);
        when(learningLeafMapper.selectCountByQuery(any())).thenReturn(2L);

        assertThrows(BusinessException.class,
                () -> learningTreeService.validateEmptyBranchLearnSkipGate(100L, 10L, 1L));
    }

    @Test
    void validateEmptyBranchLearnSkipGate_allowsEmptyBranch() {
        LearningBranch branch = branch(100L, 10L, 1L);
        when(learningBranchMapper.selectOneById(100L)).thenReturn(branch);
        when(learningLeafMapper.selectCountByQuery(any())).thenReturn(0L);

        learningTreeService.validateEmptyBranchLearnSkipGate(100L, 10L, 1L);
    }

    private static LearningDomain domain(Long id, Long userId) {
        LearningDomain d = new LearningDomain();
        d.setId(id);
        d.setUserId(userId);
        d.setName("Java");
        return d;
    }

    private static LearningBranch branch(Long id, Long domainId, Long userId) {
        LearningBranch b = new LearningBranch();
        b.setId(id);
        b.setDomainId(domainId);
        b.setUserId(userId);
        b.setParentBranchId(0L);
        b.setDepth(1);
        b.setTitle("集合");
        return b;
    }

    private static KnowledgeNote note(Long id, Long sourceId) {
        KnowledgeNote n = new KnowledgeNote();
        n.setId(id);
        n.setSourceDocumentId(sourceId);
        n.setTitle("note");
        return n;
    }

    private static SourceDocument source(Long id, Long userId) {
        SourceDocument s = new SourceDocument();
        s.setId(id);
        s.setUserId(userId);
        return s;
    }
}
