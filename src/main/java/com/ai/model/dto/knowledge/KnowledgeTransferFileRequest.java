package com.ai.model.dto.knowledge;

import lombok.Data;

/**
 * 其他模块（资料库等）把文件内容转入知识库的请求。
 * 字节内容直接随请求传递，避免调用方拼装 MultipartFile。
 */
@Data
public class KnowledgeTransferFileRequest {

    /** 原始文件名（含扩展名），用于派生标题与解析器选择 */
    private String fileName;

    private String ext;

    private String contentType;

    private byte[] content;

    /** 逗号分隔的标签名，可为空 */
    private String tags;
}
