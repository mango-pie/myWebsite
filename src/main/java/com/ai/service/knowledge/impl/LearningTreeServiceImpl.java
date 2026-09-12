package com.ai.service.knowledge.impl;

import com.ai.config.ConditionalOnModule;
import com.ai.constant.OpsAuditActionConstant;
import com.ai.constant.knowledge.KnowledgeNoteConstant;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.mapper.knowledge.KnowledgeNoteMapper;
import com.ai.mapper.knowledge.LearningBranchMapper;
import com.ai.mapper.knowledge.LearningDomainMapper;
import com.ai.mapper.knowledge.LearningLeafMapper;
import com.ai.mapper.knowledge.SourceDocumentMapper;
import com.ai.model.dto.knowledge.LearningBranchCreateRequest;
import com.ai.model.dto.knowledge.LearningBranchMergeRequest;
import com.ai.model.dto.knowledge.LearningBranchUpdateRequest;
import com.ai.model.dto.knowledge.LearningDomainCreateRequest;
import com.ai.model.dto.knowledge.LearningDomainUpdateRequest;
import com.ai.model.dto.knowledge.LearningLeafAttachRequest;
import com.ai.model.dto.knowledge.LearningLeafMoveRequest;
import com.ai.model.entity.knowledge.KnowledgeNote;
import com.ai.model.entity.knowledge.LearningBranch;
import com.ai.model.entity.knowledge.LearningDomain;
import com.ai.model.entity.knowledge.LearningLeaf;
import com.ai.model.entity.knowledge.SourceDocument;
import com.ai.model.vo.knowledge.KnowledgeNoteVO;
import com.ai.model.vo.knowledge.LearningBranchVO;
import com.ai.model.vo.knowledge.LearningDomainVO;
import com.ai.model.vo.knowledge.LearningLeafVO;
import com.ai.model.vo.knowledge.LearningTreeVO;
import com.ai.service.OpsAuditLogService;
import com.ai.service.knowledge.LearningTreeService;
import com.mybatisflex.core.logicdelete.LogicDeleteManager;
import com.mybatisflex.core.query.QueryWrapper;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@ConditionalOnModule("knowledge")
@Service
public class LearningTreeServiceImpl implements LearningTreeService {

    private static final int SNAPSHOT_MAX = 200;

    @Resource
    private LearningDomainMapper learningDomainMapper;
    @Resource
    private LearningBranchMapper learningBranchMapper;
    @Resource
    private LearningLeafMapper learningLeafMapper;
    @Resource
    private KnowledgeNoteMapper knowledgeNoteMapper;
    @Resource
    private SourceDocumentMapper sourceDocumentMapper;
    @Resource
    private OpsAuditLogService opsAuditLogService;

    @Override
    public List<LearningDomainVO> listDomains(Long userId) {
        List<LearningDomain> list = learningDomainMapper.selectListByQuery(
                QueryWrapper.create().eq("user_id", userId).orderBy("sort_order", true).orderBy("id", true));
        return list.stream().map(this::toDomainVo).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LearningDomainVO createDomain(LearningDomainCreateRequest request, Long userId) {
        String name = normalizeTitle(request == null ? null : request.getName());
        if (StrUtil.isBlank(name)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "领域名称不能为空");
        }
        assertDomainNameUnique(userId, name, null);
        LearningDomain domain = new LearningDomain();
        domain.setUserId(userId);
        domain.setName(name);
        domain.setSortOrder(request != null && request.getSortOrder() != null ? request.getSortOrder() : 0);
        domain.setCreateTime(LocalDateTime.now());
        domain.setUpdateTime(LocalDateTime.now());
        learningDomainMapper.insert(domain);
        return toDomainVo(domain);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LearningDomainVO updateDomain(Long domainId, LearningDomainUpdateRequest request, Long userId) {
        LearningDomain domain = requireDomainEntity(domainId, userId);
        if (request != null && StrUtil.isNotBlank(request.getName())) {
            String name = normalizeTitle(request.getName());
            assertDomainNameUnique(userId, name, domainId);
            domain.setName(name);
        }
        if (request != null && request.getSortOrder() != null) {
            domain.setSortOrder(request.getSortOrder());
        }
        domain.setUpdateTime(LocalDateTime.now());
        learningDomainMapper.update(domain);
        return toDomainVo(domain);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDomain(Long domainId, Long userId) {
        requireDomainEntity(domainId, userId);
        List<LearningBranch> branches = learningBranchMapper.selectListByQuery(
                QueryWrapper.create().eq("domain_id", domainId));
        for (LearningBranch branch : branches) {
            learningLeafMapper.deleteByQuery(QueryWrapper.create().eq("branch_id", branch.getId()));
            learningBranchMapper.deleteById(branch.getId());
        }
        learningLeafMapper.deleteByQuery(QueryWrapper.create().eq("domain_id", domainId));
        learningDomainMapper.deleteById(domainId);
    }

    @Override
    public LearningTreeVO getTree(Long domainId, Long userId, boolean includeLeaves) {
        LearningDomain domain = requireDomainEntity(domainId, userId);
        List<LearningBranch> all = learningBranchMapper.selectListByQuery(
                QueryWrapper.create().eq("domain_id", domainId)
                        .orderBy("parent_branch_id", true).orderBy("sort_order", true).orderBy("id", true));
        boolean truncated = all.size() > SNAPSHOT_MAX;
        List<LearningBranch> snapshot = truncated
                ? all.stream().filter(b -> b.getDepth() != null && b.getDepth() == 1).toList()
                : all;
        Map<Long, Integer> leafCounts = loadLeafCounts(domainId);
        Map<Long, int[]> mastery = loadMastery(domainId);
        Map<Long, LearningBranch> byId = all.stream().collect(Collectors.toMap(LearningBranch::getId, b -> b, (a, b) -> a));
        LearningTreeVO vo = new LearningTreeVO();
        vo.setDomain(toDomainVo(domain));
        vo.setSnapshotTruncated(truncated);
        if (truncated) {
            vo.setSnapshotMessage("枝数量超过 " + SNAPSHOT_MAX + "，快照仅含 L1，建议整理");
        }
        List<LearningBranchVO> branchVos = new ArrayList<>();
        for (LearningBranch b : snapshot) {
            LearningBranchVO bv = toBranchVo(b, domain.getName(), byId, leafCounts, mastery);
            if (includeLeaves) {
                bv.setLeaves(listLeavesByBranch(b.getId(), userId));
            }
            branchVos.add(bv);
        }
        vo.setBranches(branchVos);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LearningBranchVO createBranch(Long domainId, LearningBranchCreateRequest request, Long userId) {
        LearningDomain domain = requireDomainEntity(domainId, userId);
        String title = normalizeTitle(request == null ? null : request.getTitle());
        if (StrUtil.isBlank(title)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "枝标题不能为空");
        }
        long parentId;
        try {
            parentId = request != null ? request.resolveParentBranchId() : 0L;
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, ex.getMessage());
        }
        int depth = resolveDepth(domainId, parentId, userId);
        assertBranchTitleUnique(domainId, parentId, title, null);
        LearningBranch branch = new LearningBranch();
        branch.setDomainId(domainId);
        branch.setUserId(userId);
        branch.setParentBranchId(parentId);
        branch.setDepth(depth);
        branch.setTitle(title);
        branch.setSortOrder(request != null && request.getSortOrder() != null ? request.getSortOrder() : 0);
        branch.setCreateTime(LocalDateTime.now());
        branch.setUpdateTime(LocalDateTime.now());
        try {
            learningBranchMapper.insert(branch);
        } catch (Exception ex) {
            // 并发或软删边界下预检查可能漏掉，统一转为可读错误
            String msg = ex.getMessage() == null ? "" : ex.getMessage();
            if (msg.contains("uk_learning_branch_sibling") || msg.contains("Duplicate entry")) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "同层级下已存在同名主题枝「" + title + "」");
            }
            throw ex;
        }
        Map<Long, LearningBranch> byId = Map.of(branch.getId(), branch);
        return toBranchVo(branch, domain.getName(), byId, Map.of(branch.getId(), 0), Map.of());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LearningBranchVO updateBranch(Long branchId, LearningBranchUpdateRequest request, Long userId) {
        LearningBranch branch = requireBranchEntity(branchId, userId);
        LearningDomain domain = requireDomainEntity(branch.getDomainId(), userId);
        if (request != null && StrUtil.isNotBlank(request.getTitle())) {
            String title = normalizeTitle(request.getTitle());
            assertBranchTitleUnique(branch.getDomainId(), branch.getParentBranchId(), title, branchId);
            branch.setTitle(title);
        }
        if (request != null && request.getSortOrder() != null) {
            branch.setSortOrder(request.getSortOrder());
        }
        if (request != null && request.getParentBranchId() != null
                && !Objects.equals(request.getParentBranchId(), branch.getParentBranchId())) {
            long newParent = request.getParentBranchId();
            int depth = resolveDepth(branch.getDomainId(), newParent, userId);
            branch.setParentBranchId(newParent);
            branch.setDepth(depth);
        }
        branch.setUpdateTime(LocalDateTime.now());
        learningBranchMapper.update(branch);
        Map<Long, Integer> counts = loadLeafCounts(branch.getDomainId());
        Map<Long, int[]> mastery = loadMastery(branch.getDomainId());
        Map<Long, LearningBranch> byId = loadBranchMap(branch.getDomainId());
        return toBranchVo(branch, domain.getName(), byId, counts, mastery);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBranch(Long branchId, Long userId) {
        LearningBranch branch = requireBranchEntity(branchId, userId);
        long leafCount = learningLeafMapper.selectCountByQuery(
                QueryWrapper.create().eq("branch_id", branchId));
        if (leafCount > 0) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "该枝下仍有文章，请先移走或取消挂载");
        }
        List<LearningBranch> children = learningBranchMapper.selectListByQuery(
                QueryWrapper.create().eq("parent_branch_id", branchId));
        if (!children.isEmpty()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "请先处理子主题枝");
        }
        learningBranchMapper.deleteById(branch.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void mergeBranch(Long sourceBranchId, LearningBranchMergeRequest request, Long userId) {
        if (request == null || request.getTargetBranchId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "targetBranchId 不能为空");
        }
        LearningBranch source = requireBranchEntity(sourceBranchId, userId);
        LearningBranch target = requireBranchEntity(request.getTargetBranchId(), userId);
        if (!source.getDomainId().equals(target.getDomainId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "只能合并同一领域下的枝");
        }
        if (source.getId().equals(target.getId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "源枝与目标枝相同");
        }
        List<LearningLeaf> leaves = learningLeafMapper.selectListByQuery(
                QueryWrapper.create().eq("branch_id", source.getId()));
        for (LearningLeaf leaf : leaves) {
            leaf.setBranchId(target.getId());
            leaf.setUpdateTime(LocalDateTime.now());
            learningLeafMapper.update(leaf);
        }
        List<LearningBranch> childBranches = learningBranchMapper.selectListByQuery(
                QueryWrapper.create().eq("parent_branch_id", source.getId()));
        if (!childBranches.isEmpty()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "源枝存在 L2 子枝，请先移动后再合并");
        }
        learningBranchMapper.deleteById(source.getId());
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("sourceBranchId", String.valueOf(sourceBranchId));
        detail.put("sourceBranchTitle", source.getTitle());
        detail.put("targetBranchId", String.valueOf(target.getId()));
        detail.put("movedLeaves", String.valueOf(leaves.size()));
        opsAuditLogService.audit(
                OpsAuditActionConstant.LEARNING_BRANCH_MERGE,
                userId,
                OpsAuditActionConstant.RESOURCE_LEARNING_BRANCH,
                String.valueOf(sourceBranchId),
                true,
                detail,
                null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LearningLeafVO attachLeaf(LearningLeafAttachRequest request, Long userId) {
        if (request == null || request.getDomainId() == null || request.getNoteId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "domainId 与 noteId 不能为空");
        }
        requireDomainEntity(request.getDomainId(), userId);
        KnowledgeNote note = requireOwnedNote(request.getNoteId(), userId);
        Long branchId = request.getBranchId();
        if (branchId == null && StrUtil.isNotBlank(request.getNewBranchTitle())) {
            LearningBranchCreateRequest br = new LearningBranchCreateRequest();
            br.setTitle(request.getNewBranchTitle());
            br.setParentBranchId(
                    request.getParentBranchId() != null ? String.valueOf(request.getParentBranchId()) : "0");
            branchId = createBranch(request.getDomainId(), br, userId).getId();
        }
        if (branchId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "branchId 或 newBranchTitle 必填其一");
        }
        LearningBranch branch = requireBranchEntity(branchId, userId);
        if (!branch.getDomainId().equals(request.getDomainId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "枝不属于该领域");
        }
        LocalDateTime now = LocalDateTime.now();
        LearningLeaf existing = findLeafByNoteIdIncludingDeleted(request.getNoteId());
        if (existing != null) {
            existing.setDomainId(request.getDomainId());
            existing.setBranchId(branchId);
            existing.setUserId(userId);
            existing.setAttachedAt(now);
            existing.setUpdateTime(now);
            existing.setIsDelete(0);
            learningLeafMapper.update(existing);
            return toLeafVo(existing, note);
        }
        LearningLeaf leaf = new LearningLeaf();
        leaf.setDomainId(request.getDomainId());
        leaf.setBranchId(branchId);
        leaf.setUserId(userId);
        leaf.setKnowledgeNoteId(request.getNoteId());
        leaf.setSortOrder(0);
        leaf.setAttachedAt(now);
        leaf.setCreateTime(now);
        leaf.setUpdateTime(now);
        learningLeafMapper.insert(leaf);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("noteId", String.valueOf(request.getNoteId()));
        detail.put("branchId", String.valueOf(branchId));
        detail.put("branchTitle", branch.getTitle());
        detail.put("resurrected", "false");
        opsAuditLogService.audit(
                OpsAuditActionConstant.LEARNING_LEAF_ATTACH,
                userId,
                OpsAuditActionConstant.RESOURCE_LEARNING_LEAF,
                String.valueOf(request.getNoteId()),
                true,
                detail,
                null);
        return toLeafVo(leaf, note);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LearningLeafVO moveLeaf(LearningLeafMoveRequest request, Long userId) {
        if (request == null || request.getNoteId() == null || request.getTargetBranchId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "noteId 与 targetBranchId 不能为空");
        }
        KnowledgeNote note = requireOwnedNote(request.getNoteId(), userId);
        LearningLeaf leaf = learningLeafMapper.selectOneByQuery(
                QueryWrapper.create().eq("knowledge_note_id", request.getNoteId()));
        if (leaf == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "该 note 未挂载到树上");
        }
        LearningBranch target = requireBranchEntity(request.getTargetBranchId(), userId);
        Long fromBranchId = leaf.getBranchId();
        leaf.setBranchId(target.getId());
        leaf.setDomainId(target.getDomainId());
        leaf.setUpdateTime(LocalDateTime.now());
        learningLeafMapper.update(leaf);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("noteId", String.valueOf(request.getNoteId()));
        detail.put("fromBranchId", String.valueOf(fromBranchId));
        detail.put("toBranchId", String.valueOf(target.getId()));
        opsAuditLogService.audit(
                OpsAuditActionConstant.LEARNING_LEAF_MOVE,
                userId,
                OpsAuditActionConstant.RESOURCE_LEARNING_LEAF,
                String.valueOf(request.getNoteId()),
                true,
                detail,
                null);
        return toLeafVo(leaf, note);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void detachLeafByNote(Long noteId, Long userId) {
        requireOwnedNote(noteId, userId);
        LearningLeaf leaf = learningLeafMapper.selectOneByQuery(
                QueryWrapper.create().eq("knowledge_note_id", noteId));
        if (leaf == null) {
            return;
        }
        learningLeafMapper.deleteById(leaf.getId());
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("noteId", String.valueOf(noteId));
        detail.put("branchId", String.valueOf(leaf.getBranchId()));
        opsAuditLogService.audit(
                OpsAuditActionConstant.LEARNING_LEAF_DETACH,
                userId,
                OpsAuditActionConstant.RESOURCE_LEARNING_LEAF,
                String.valueOf(noteId),
                true,
                detail,
                null);
    }

    @Override
    public List<LearningLeafVO> listLeavesByBranch(Long branchId, Long userId) {
        requireBranchEntity(branchId, userId);
        List<LearningLeaf> leaves = learningLeafMapper.selectListByQuery(
                QueryWrapper.create().eq("branch_id", branchId)
                        .orderBy("sort_order", true).orderBy("attached_at", false));
        List<LearningLeafVO> result = new ArrayList<>();
        for (LearningLeaf leaf : leaves) {
            KnowledgeNote note = knowledgeNoteMapper.selectOneById(leaf.getKnowledgeNoteId());
            if (note != null) {
                result.add(toLeafVo(leaf, note));
            }
        }
        return result;
    }

    @Override
    public List<KnowledgeNoteVO> listUnattachedNotes(Long userId, int pageNum, int pageSize) {
        int pn = Math.max(pageNum, 1);
        int ps = Math.min(Math.max(pageSize, 1), 100);
        Set<Long> attachedNoteIds = new HashSet<>();
        List<LearningLeaf> attached = learningLeafMapper.selectListByQuery(
                QueryWrapper.create().eq("user_id", userId));
        for (LearningLeaf leaf : attached) {
            attachedNoteIds.add(leaf.getKnowledgeNoteId());
        }
        List<SourceDocument> ownedSources = sourceDocumentMapper.selectListByQuery(
                QueryWrapper.create().eq("user_id", userId));
        if (ownedSources.isEmpty()) {
            return List.of();
        }
        Set<Long> sourceIds = ownedSources.stream().map(SourceDocument::getId).collect(Collectors.toSet());
        List<KnowledgeNote> allNotes = knowledgeNoteMapper.selectListByQuery(
                QueryWrapper.create()
                        .in("source_document_id", sourceIds)
                        .eq("status", KnowledgeNoteConstant.STATUS_SUCCESS)
                        .orderBy("update_time", false));
        List<KnowledgeNote> unattached = allNotes.stream()
                .filter(n -> !attachedNoteIds.contains(n.getId()))
                .toList();
        int from = (pn - 1) * ps;
        if (from >= unattached.size()) {
            return List.of();
        }
        int to = Math.min(from + ps, unattached.size());
        List<KnowledgeNoteVO> vos = new ArrayList<>();
        for (KnowledgeNote note : unattached.subList(from, to)) {
            KnowledgeNoteVO vo = new KnowledgeNoteVO();
            vo.setId(note.getId());
            vo.setTitle(note.getTitle());
            vo.setPublishStatus(note.getPublishStatus());
            vo.setIndexStatus(note.getIndexStatus());
            vo.setUpdateTime(note.getUpdateTime());
            vos.add(vo);
        }
        return vos;
    }

    @Override
    public LearningDomainVO requireDomain(Long domainId, Long userId) {
        return toDomainVo(requireDomainEntity(domainId, userId));
    }

    @Override
    public LearningBranchVO requireBranch(Long branchId, Long userId) {
        LearningBranch branch = requireBranchEntity(branchId, userId);
        LearningDomain domain = requireDomainEntity(branch.getDomainId(), userId);
        Map<Long, LearningBranch> byId = loadBranchMap(branch.getDomainId());
        Map<Long, Integer> counts = loadLeafCounts(branch.getDomainId());
        Map<Long, int[]> mastery = loadMastery(branch.getDomainId());
        return toBranchVo(branch, domain.getName(), byId, counts, mastery);
    }

    @Override
    public void validateEmptyBranchLearnSkipGate(Long branchId, Long domainId, Long userId) {
        LearningBranch branch = requireBranchEntity(branchId, userId);
        if (domainId != null && !branch.getDomainId().equals(domainId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "branch 与 domain 不匹配");
        }
        long leafCount = learningLeafMapper.selectCountByQuery(
                QueryWrapper.create().eq("branch_id", branchId));
        if (leafCount > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "该枝已有文章，不可跳过门闩，请先走门闩判定");
        }
    }

    private LearningDomain requireDomainEntity(Long domainId, Long userId) {
        if (domainId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "domainId 不能为空");
        }
        LearningDomain domain = learningDomainMapper.selectOneById(domainId);
        if (domain == null || !domain.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "领域不存在");
        }
        return domain;
    }

    private LearningBranch requireBranchEntity(Long branchId, Long userId) {
        if (branchId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "branchId 不能为空");
        }
        LearningBranch branch = learningBranchMapper.selectOneById(branchId);
        if (branch == null || !branch.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "主题枝不存在");
        }
        return branch;
    }

    private KnowledgeNote requireOwnedNote(Long noteId, Long userId) {
        KnowledgeNote note = knowledgeNoteMapper.selectOneById(noteId);
        if (note == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "精读 note 不存在");
        }
        SourceDocument source = sourceDocumentMapper.selectOneById(note.getSourceDocumentId());
        if (source == null || !source.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权操作该 note");
        }
        return note;
    }

    private LearningLeaf findLeafByNoteIdIncludingDeleted(Long noteId) {
        return LogicDeleteManager.execWithoutLogicDelete(() ->
                learningLeafMapper.selectOneByQuery(
                        QueryWrapper.create().eq("knowledge_note_id", noteId)));
    }

    private void assertDomainNameUnique(Long userId, String name, Long excludeId) {
        QueryWrapper q = QueryWrapper.create().eq("user_id", userId).eq("name", name);
        LearningDomain existing = learningDomainMapper.selectOneByQuery(q);
        if (existing != null && (excludeId == null || !existing.getId().equals(excludeId))) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "已存在同名领域");
        }
    }

    private void assertBranchTitleUnique(Long domainId, long parentBranchId, String title, Long excludeId) {
        // 含软删行：同名活跃枝冲突；若仅软删同名则可新建（uk 含 is_delete）
        QueryWrapper q = QueryWrapper.create()
                .eq("domain_id", domainId)
                .eq("parent_branch_id", parentBranchId)
                .eq("title", title);
        LearningBranch existing = learningBranchMapper.selectOneByQuery(q);
        if (existing != null && (excludeId == null || !existing.getId().equals(excludeId))) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "同层级下已存在同名主题枝「" + title + "」");
        }
    }

    private int resolveDepth(Long domainId, long parentBranchId, Long userId) {
        if (parentBranchId == 0L) {
            return 1;
        }
        LearningBranch parent = requireBranchEntity(parentBranchId, userId);
        if (!parent.getDomainId().equals(domainId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "父枝不属于该领域");
        }
        if (parent.getDepth() != null && parent.getDepth() >= 2) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "主题深度不能超过 2");
        }
        return 2;
    }

    private Map<Long, Integer> loadLeafCounts(Long domainId) {
        List<LearningLeaf> leaves = learningLeafMapper.selectListByQuery(
                QueryWrapper.create().eq("domain_id", domainId));
        Map<Long, Integer> counts = new HashMap<>();
        for (LearningLeaf leaf : leaves) {
            counts.merge(leaf.getBranchId(), 1, Integer::sum);
        }
        return counts;
    }

    /**
     * 枝级掌握度：branchId → [masteredCount, total]，基于挂叶 note 的复习状态聚合。
     */
    private Map<Long, int[]> loadMastery(Long domainId) {
        List<LearningLeaf> leaves = learningLeafMapper.selectListByQuery(
                QueryWrapper.create().eq("domain_id", domainId));
        Map<Long, int[]> mastery = new HashMap<>();
        if (leaves.isEmpty()) {
            return mastery;
        }
        List<Long> noteIds = leaves.stream().map(LearningLeaf::getKnowledgeNoteId).toList();
        List<KnowledgeNote> notes = knowledgeNoteMapper.selectListByQuery(
                QueryWrapper.create().in("id", noteIds));
        Map<Long, String> reviewByNote = notes.stream()
                .collect(Collectors.toMap(KnowledgeNote::getId,
                        n -> n.getReviewStatus() == null ? KnowledgeNoteConstant.REVIEW_NEW : n.getReviewStatus(),
                        (a, b) -> a));
        for (LearningLeaf leaf : leaves) {
            int[] m = mastery.computeIfAbsent(leaf.getBranchId(), k -> new int[]{0, 0});
            m[1]++;
            if (KnowledgeNoteConstant.REVIEW_MASTERED.equals(reviewByNote.get(leaf.getKnowledgeNoteId()))) {
                m[0]++;
            }
        }
        return mastery;
    }

    private Map<Long, LearningBranch> loadBranchMap(Long domainId) {
        return learningBranchMapper.selectListByQuery(QueryWrapper.create().eq("domain_id", domainId))
                .stream().collect(Collectors.toMap(LearningBranch::getId, b -> b, (a, b) -> a));
    }

    private String normalizeTitle(String title) {
        return title == null ? null : title.trim();
    }

    private LearningDomainVO toDomainVo(LearningDomain domain) {
        LearningDomainVO vo = new LearningDomainVO();
        vo.setId(domain.getId());
        vo.setName(domain.getName());
        vo.setSortOrder(domain.getSortOrder());
        vo.setCreateTime(domain.getCreateTime());
        return vo;
    }

    private LearningBranchVO toBranchVo(LearningBranch branch, String domainName,
                                        Map<Long, LearningBranch> byId, Map<Long, Integer> leafCounts) {
        return toBranchVo(branch, domainName, byId, leafCounts, Map.of());
    }

    private LearningBranchVO toBranchVo(LearningBranch branch, String domainName,
                                        Map<Long, LearningBranch> byId, Map<Long, Integer> leafCounts,
                                        Map<Long, int[]> mastery) {
        LearningBranchVO vo = new LearningBranchVO();
        vo.setId(branch.getId());
        vo.setDomainId(branch.getDomainId());
        vo.setParentBranchId(branch.getParentBranchId());
        vo.setDepth(branch.getDepth());
        vo.setTitle(branch.getTitle());
        vo.setSortOrder(branch.getSortOrder());
        vo.setLeafCount(leafCounts.getOrDefault(branch.getId(), 0));
        int[] m = mastery.get(branch.getId());
        if (m != null && m[1] > 0) {
            vo.setMasteredCount(m[0]);
            vo.setMasteryPercent((int) Math.round(m[0] * 100.0 / m[1]));
        } else {
            vo.setMasteredCount(0);
            vo.setMasteryPercent(0);
        }
        vo.setPath(buildPath(domainName, branch, byId));
        return vo;
    }

    private String buildPath(String domainName, LearningBranch branch, Map<Long, LearningBranch> byId) {
        if (branch.getParentBranchId() == null || branch.getParentBranchId() == 0L) {
            return domainName + " > " + branch.getTitle();
        }
        LearningBranch parent = byId.get(branch.getParentBranchId());
        if (parent == null) {
            return domainName + " > " + branch.getTitle();
        }
        return domainName + " > " + parent.getTitle() + " > " + branch.getTitle();
    }

    private LearningLeafVO toLeafVo(LearningLeaf leaf, KnowledgeNote note) {
        LearningLeafVO vo = new LearningLeafVO();
        vo.setId(leaf.getId());
        vo.setBranchId(leaf.getBranchId());
        vo.setNoteId(leaf.getKnowledgeNoteId());
        vo.setNoteTitle(note.getTitle());
        vo.setPublishStatus(note.getPublishStatus());
        vo.setIndexStatus(note.getIndexStatus());
        vo.setReviewStatus(note.getReviewStatus());
        vo.setAttachedAt(leaf.getAttachedAt());
        return vo;
    }
}
