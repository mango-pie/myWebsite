package com.ai.service.knowledge;

import com.ai.model.dto.knowledge.KnowledgeNoteQueryRequest;
import com.ai.model.dto.knowledge.KnowledgeNoteUpdateRequest;
import com.ai.model.entity.knowledge.KnowledgeNote;
import com.ai.model.vo.knowledge.KnowledgeNoteDetailVO;
import com.ai.model.vo.knowledge.KnowledgeNoteVO;
import com.ai.model.vo.knowledge.KnowledgeTagVO;
import com.mybatisflex.core.paginate.Page;

import java.util.List;

public interface KnowledgeNoteService {

    Page<KnowledgeNoteVO> page(KnowledgeNoteQueryRequest request, Long userId);

    KnowledgeNoteDetailVO getDetail(Long noteId, Long userId);

    KnowledgeNoteVO update(Long noteId, KnowledgeNoteUpdateRequest request, Long userId);

    boolean delete(Long noteId, Long userId);

    /** 复习状态流转：NEW / REVIEWING / MASTERED */
    KnowledgeNoteVO updateReviewStatus(Long noteId, String reviewStatus, Long userId);

    /** 归档 / 取消归档：列表默认隐藏归档，树与来源关联保留 */
    KnowledgeNoteVO archive(Long noteId, boolean archived, Long userId);

    /** 整库 Markdown 批量导出（zip），备份 / 迁移用 */
    byte[] exportAll(Long userId);

    /** 标签统计（排除 agentQuery 标记） */
    List<KnowledgeTagVO> listTags(Long userId);

    /** 标签重命名（合并）：返回受影响 note 数 */
    int renameTag(String oldName, String newName, Long userId);

    /** 删除标签：返回受影响 note 数 */
    int deleteTag(String name, Long userId);

    /**
     * 博客软删后回写：清空关联精读的 blogPostId，publishStatus 置为未发布。
     * 无关联 note 时幂等 no-op。
     */
    void clearBlogLinkByPostId(Long blogPostId, Long userId);

    KnowledgeNote requireOwned(Long noteId, Long userId);

    KnowledgeNoteVO toVO(KnowledgeNote note);
}
