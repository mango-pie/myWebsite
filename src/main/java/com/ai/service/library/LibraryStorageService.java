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

    /** 服务端中转上传（浏览器打不到内网 MinIO 时使用） */
    void put(String bucket, String objectKey, java.io.InputStream stream, long objectSize, String contentType);

    /** 把对象流式写出（避免整文件进内存） */
    void writeTo(String bucket, String objectKey, java.io.OutputStream out);

    void remove(String bucket, String objectKey);

    void ensureBucket(String bucket);
}
