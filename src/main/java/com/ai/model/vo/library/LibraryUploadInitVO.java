package com.ai.model.vo.library;

import lombok.Data;

/**
 * upload-init 返回：秒传命中给 mode=instant + file；否则 mode=direct，
 * 前端拿 uploadUrl（预签名 PUT 直传 MinIO）后调 upload-complete。
 */
@Data
public class LibraryUploadInitVO {

    /** instant / direct */
    private String mode;
    private LibraryFileVO file;
    private Long fileId;
    private String uploadUrl;
    private String objectKey;
}
