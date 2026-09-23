package com.ai.service.knowledge;

import com.ai.model.dto.knowledge.KnowledgeTransferFileRequest;
import com.ai.model.dto.knowledge.KnowledgeTransferResult;

/**
 * 供其他模块（资料库等）把文件转入知识库的窄门面。
 * 返回中立 DTO，不暴露 knowledge entity；knowledge 模块未启用时本 Bean 不存在，
 * 调用方须以 ObjectProvider 方式注入并判空。
 */
public interface KnowledgeTransferService {

    /**
     * 入库 → 精读合蒸 → 向量索引 的完整转移链路。
     * 任一前置步骤失败时抛出异常；向量索引失败不视为整体失败（结果里 indexed=false）。
     */
    KnowledgeTransferResult transferFile(KnowledgeTransferFileRequest request, Long userId);
}
