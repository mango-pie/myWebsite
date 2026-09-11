package com.ai.service.knowledge;

public interface KnowledgeStorageService {

    String upload(String bucket, String objectKey, byte[] content, String contentType);

    byte[] download(String bucket, String objectKey);

    void delete(String bucket, String objectKey);

    String getPresignedUrl(String bucket, String objectKey, int expireSeconds);
}
