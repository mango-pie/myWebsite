package com.ai.service.library;

/**
 * 资料库对象存储：与知识库共用 MinIO 凭据，bucket/对象键由资料库自管。
 */
public interface LibraryStorageService {

    /** 预签名 PUT 直传地址 */
    String presignedPutUrl(String bucket, String objectKey, int expireSeconds);

    /** 预签名 GET 下载地址 */
    String presignedGetUrl(String bucket, String objectKey, int expireSeconds);

    /** 对象是否存在，存在返回其字节数 */
    Long statSize(String bucket, String objectKey);

    byte[] download(String bucket, String objectKey);

    void remove(String bucket, String objectKey);

    void ensureBucket(String bucket);
}
