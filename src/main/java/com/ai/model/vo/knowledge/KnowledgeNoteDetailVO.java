package com.ai.model.vo.knowledge;

import com.ai.model.entity.knowledge.SourceDocument;
import com.ai.model.vo.blog.BlogPostVO;
import lombok.Data;

@Data
public class KnowledgeNoteDetailVO {

    private KnowledgeNoteVO note;
    private SourceDocument source;
    private String rawTextSummary;
    private String rawText;
    private String distilledMd;
    private BlogPostVO blogPost;
    private KnowledgeDocumentVO knowledgeDocument;
}
