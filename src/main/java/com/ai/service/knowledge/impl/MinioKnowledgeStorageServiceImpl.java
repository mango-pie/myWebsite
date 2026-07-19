package com.ai.service.knowledge.impl;

import com.ai.config.ConditionalOnModule;

import com.ai.config.knowledge.KnowledgeMinioProperties;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.service.IntegrationCredentialsService;
import com.ai.service.knowledge.KnowledgeStorageService;
import com.ai.setting.IntegrationClientCache;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;

@Slf4j
@ConditionalOnModule("knowledge")
@Service
public class MinioKnowledgeStorageServiceImpl implements KnowledgeStorageService {

    private final KnowledgeMinioProperties properties;

    @Resource
    private IntegrationCredentialsService credentials;

    @Resource
    private IntegrationClientCache integrationClientCache;

    private volatile long cachedVersion = -1;
    private volatile String cachedFingerprint;
    private volatile MinioClient cachedClient;

    public MinioKnowledgeStorageServiceImpl(KnowledgeMinioProperties properties) {
        this.properties = properties;
    }

    @Override
    public String upload(String bucket, String objectKey, byte[] content, String contentType) {
        if (!credentials.minioEnabled()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "知识库 MinIO 未启用");
        }
        try {
            MinioClient client = client();
            ensureBucket(client, bucket);
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .contentType(contentType)
                    .build());
            return objectKey;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Upload knowledge file failed, bucket={}, objectKey={}", bucket, objectKey, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "知识库文件上传失败");
        }
    }

    @Override
    public byte[] download(String bucket, String objectKey) {
        try (var stream = client().getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .build())) {
            return stream.readAllBytes();
        } catch (Exception e) {
            log.error("Download knowledge file failed, bucket={}, objectKey={}", bucket, objectKey, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "读取知识库文件失败");
        }
    }

    @Override
    public void delete(String bucket, String objectKey) {
        try {
            client().removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            log.warn("Delete knowledge object failed, bucket={}, objectKey={}", bucket, objectKey, e);
        }
    }

    @Override
    public String getPresignedUrl(String bucket, String objectKey, int expireSeconds) {
        try {
            return client().getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry(expireSeconds)
                    .build());
        } catch (Exception e) {
            log.error("Generate knowledge download url failed, bucket={}, objectKey={}", bucket, objectKey, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取知识库文件下载链接失败");
        }
    }

    private MinioClient client() {
        String endpoint = credentials.minioEndpoint();
        String accessKey = credentials.minioAccessKey();
        String secretKey = credentials.minioSecretKey();
        String fingerprint = endpoint + "|" + accessKey + "|" + secretKey;
        long version = integrationClientCache.version();
        if (cachedClient != null && version == cachedVersion && fingerprint.equals(cachedFingerprint)) {
            return cachedClient;
        }
        MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        cachedClient = client;
        cachedFingerprint = fingerprint;
        cachedVersion = version;
        return client;
    }

    private void ensureBucket(MinioClient client, String bucket) throws Exception {
        boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }
}
