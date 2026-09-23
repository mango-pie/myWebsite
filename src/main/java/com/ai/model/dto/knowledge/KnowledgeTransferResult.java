package com.ai.model.dto.knowledge;

import lombok.Builder;
import lombok.Data;

/**
 * 转入知识库的结果：中立 DTO，只暴露跨模块需要的标识信息，不回传 entity。
 */
@Data
@Builder
public class KnowledgeTransferResult {

    private Long sourceDocumentId;

    private Long noteId;

    private String noteTitle;

    /** 是否已完成向量索引（索引失败不代表入库失败） */
    private boolean indexed;
}
