package com.ai.service.knowledge;

import com.ai.model.dto.knowledge.KnowledgeNoteQueryRequest;
import com.ai.model.dto.knowledge.KnowledgeNoteUpdateRequest;
import com.ai.model.entity.knowledge.KnowledgeNote;
import com.ai.model.vo.knowledge.KnowledgeNoteDetailVO;
import com.ai.model.vo.knowledge.KnowledgeNoteVO;
import com.mybatisflex.core.paginate.Page;

public interface KnowledgeNoteService {

    Page<KnowledgeNoteVO> page(KnowledgeNoteQueryRequest request, Long userId);

    KnowledgeNoteDetailVO getDetail(Long noteId, Long userId);

    KnowledgeNoteVO update(Long noteId, KnowledgeNoteUpdateRequest request, Long userId);

    boolean delete(Long noteId, Long userId);

    KnowledgeNote requireOwned(Long noteId, Long userId);

    KnowledgeNoteVO toVO(KnowledgeNote note);

    /**
     * 博客软删后回写：清空关联精读的 blogPostId，publishStatus 置为未发布。
     * 无关联 note 时幂等 no-op。
     */
    void clearBlogLinkByPostId(Long blogPostId, Long userId);
}
