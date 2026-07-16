package com.ai.service.knowledge.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.ai.constant.OpsAuditActionConstant;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.mapper.knowledge.KnowledgeBaseMapper;
import com.ai.mapper.knowledge.KnowledgeDocumentMapper;
import com.ai.model.dto.knowledge.KnowledgeBaseCreateRequest;
import com.ai.model.dto.knowledge.KnowledgeBaseQueryRequest;
import com.ai.model.dto.knowledge.KnowledgeBaseUpdateRequest;
import com.ai.model.entity.knowledge.KnowledgeBase;
import com.ai.model.entity.knowledge.KnowledgeDocument;
import com.ai.model.vo.knowledge.KnowledgeBaseVO;
import com.ai.service.OpsAuditLogService;
import com.ai.service.knowledge.KnowledgeBaseService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class KnowledgeBaseServiceImpl extends ServiceImpl<KnowledgeBaseMapper, KnowledgeBase>
        implements KnowledgeBaseService {

    @Resource
    private KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Resource
    private OpsAuditLogService opsAuditLogService;

    @Override
    public KnowledgeBaseVO create(KnowledgeBaseCreateRequest request, Long userId) {
        if (request == null || StrUtil.isBlank(request.getName())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "知识库名称不能为空");
        }
        checkDuplicateName(userId, request.getName(), null);
        LocalDateTime now = LocalDateTime.now();
        KnowledgeBase entity = new KnowledgeBase();
        entity.setName(request.getName().trim());
        entity.setDescription(request.getDescription());
        entity.setUserId(userId);
        entity.setVisibility(StrUtil.blankToDefault(request.getVisibility(), "PRIVATE"));
        entity.setStatus(1);
        entity.setDocumentCount(0);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        entity.setIsDelete(0);
        if (!this.save(entity)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "创建知识库失败");
        }
        return toVO(entity);
    }

    @Override
    public KnowledgeBaseVO update(Long id, KnowledgeBaseUpdateRequest request, Long userId) {
        KnowledgeBase existing = requireOwned(id, userId);
        if (request == null || StrUtil.isBlank(request.getName())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "知识库名称不能为空");
        }
        checkDuplicateName(userId, request.getName(), id);
        KnowledgeBase update = new KnowledgeBase();
        update.setId(existing.getId());
        update.setName(request.getName().trim());
        update.setDescription(request.getDescription());
        update.setVisibility(StrUtil.blankToDefault(request.getVisibility(), "PRIVATE"));
        update.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        update.setUpdateTime(LocalDateTime.now());
        if (!this.updateById(update)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "修改知识库失败");
        }
        return getVO(id, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(Long id, Long userId) {
        KnowledgeBase existing = requireOwned(id, userId);
        knowledgeDocumentMapper.deleteByQuery(QueryWrapper.create()
                .eq("knowledge_base_id", existing.getId())
                .eq("user_id", userId));
        boolean removed = this.removeById(existing.getId());
        if (removed) {
            opsAuditLogService.audit(
                    OpsAuditActionConstant.KNOWLEDGE_BASE_DELETE,
                    userId,
                    OpsAuditActionConstant.RESOURCE_KNOWLEDGE_BASE,
                    String.valueOf(existing.getId()),
                    true,
                    Map.of("name", StrUtil.blankToDefault(existing.getName(), "")),
                    null);
        }
        return removed;
    }

    @Override
    public KnowledgeBaseVO getVO(Long id, Long userId) {
        return toVO(requireOwned(id, userId));
    }

    @Override
    public Page<KnowledgeBaseVO> page(KnowledgeBaseQueryRequest request, Long userId) {
        int pageNum = request == null ? 1 : request.getPageNum();
        int pageSize = request == null ? 10 : request.getPageSize();
        QueryWrapper wrapper = QueryWrapper.create()
                .eq("user_id", userId)
                .like("name", request == null ? null : request.getName(), request != null && StrUtil.isNotBlank(request.getName()))
                .eq("visibility", request == null ? null : request.getVisibility(), request != null && StrUtil.isNotBlank(request.getVisibility()))
                .eq("status", request == null ? null : request.getStatus(), request != null && request.getStatus() != null)
                .orderBy("update_time", false);
        Page<KnowledgeBase> page = this.page(Page.of(pageNum, pageSize), wrapper);
        List<KnowledgeBaseVO> records = page.getRecords().stream().map(this::toVO).toList();
        Page<KnowledgeBaseVO> voPage = Page.of(page.getPageNumber(), page.getPageSize(), page.getTotalRow());
        voPage.setRecords(records);
        return voPage;
    }

    @Override
    public KnowledgeBase requireOwned(Long id, Long userId) {
        if (id == null || userId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        KnowledgeBase entity = this.getById(id);
        if (entity == null || !userId.equals(entity.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识库不存在");
        }
        return entity;
    }

    private void checkDuplicateName(Long userId, String name, Long excludeId) {
        QueryWrapper wrapper = QueryWrapper.create()
                .eq("user_id", userId)
                .eq("name", name);
        if (excludeId != null) {
            wrapper.ne("id", excludeId);
        }
        if (this.count(wrapper) > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "同一用户下知识库名称不能重复");
        }
    }

    private KnowledgeBaseVO toVO(KnowledgeBase entity) {
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        BeanUtil.copyProperties(entity, vo);
        return vo;
    }
}
