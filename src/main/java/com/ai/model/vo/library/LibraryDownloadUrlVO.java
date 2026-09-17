package com.ai.model.vo.library;

import lombok.Data;

@Data
public class LibraryDownloadUrlVO {

    private String url;

    /** 预签名有效期（秒） */
    private int expiresIn;
}
