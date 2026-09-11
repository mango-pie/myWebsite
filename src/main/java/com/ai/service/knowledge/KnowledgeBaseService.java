package com.ai.service.knowledge;

import com.ai.model.dto.knowledge.KnowledgeBaseCreateRequest;
import com.ai.model.dto.knowledge.KnowledgeBaseQueryRequest;
import com.ai.model.dto.knowledge.KnowledgeBaseUpdateRequest;
import com.ai.model.entity.knowledge.KnowledgeBase;
import com.ai.model.vo.knowledge.KnowledgeBaseVO;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.service.IService;

public interface KnowledgeBaseService extends IService<KnowledgeBase> {

    KnowledgeBaseVO create(KnowledgeBaseCreateRequest request, Long userId);

    KnowledgeBaseVO update(Long id, KnowledgeBaseUpdateRequest request, Long userId);

    boolean delete(Long id, Long userId);

    KnowledgeBaseVO getVO(Long id, Long userId);

    Page<KnowledgeBaseVO> page(KnowledgeBaseQueryRequest request, Long userId);

    KnowledgeBase requireOwned(Long id, Long userId);
}
