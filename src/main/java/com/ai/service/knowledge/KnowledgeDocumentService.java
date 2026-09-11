package com.ai.service.knowledge;

import com.ai.model.dto.knowledge.KnowledgeDocumentQueryRequest;
import com.ai.model.entity.knowledge.KnowledgeDocument;
import com.ai.model.vo.knowledge.KnowledgeChunkVO;
import com.ai.model.vo.knowledge.KnowledgeDocumentVO;
import com.ai.model.vo.knowledge.KnowledgeDownloadUrlVO;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.service.IService;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface KnowledgeDocumentService extends IService<KnowledgeDocument> {

    KnowledgeDocumentVO upload(Long knowledgeBaseId, MultipartFile file, Long userId);

    Page<KnowledgeDocumentVO> pageByKnowledgeBase(Long knowledgeBaseId, KnowledgeDocumentQueryRequest request, Long userId);

    KnowledgeDocumentVO getVO(Long id, Long userId);

    boolean delete(Long id, Long userId);

    KnowledgeDownloadUrlVO getDownloadUrl(Long id, Long userId);

    KnowledgeDocumentVO parse(Long id, Long userId);

    List<KnowledgeChunkVO> listChunks(Long id, int pageNum, int pageSize, Long userId);

    KnowledgeDocument requireOwned(Long id, Long userId);
}
