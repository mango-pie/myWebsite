package com.ai.model.dto.library;

import lombok.Data;

@Data
public class LibraryUploadInitRequest {

    private String fileName;

    private Long size;

    /** 文件内容 SHA-256 十六进制（64 位），用于秒传去重 */
    private String sha256;
}
