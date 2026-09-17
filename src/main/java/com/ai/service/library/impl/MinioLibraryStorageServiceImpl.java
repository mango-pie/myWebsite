package com.ai.service.library.impl;

import com.ai.config.ConditionalOnModule;

import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.service.IntegrationCredentialsService;
import com.ai.service.library.LibraryStorageService;
import com.ai.setting.IntegrationClientCache;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@ConditionalOnModule("library")
@Service
public class MinioLibraryStorageServiceImpl implements LibraryStorageService {

    @Resource
    private IntegrationCredentialsService credentials;

    @Resource
    private IntegrationClientCache integrationClientCache;

    private volatile long cachedVersion = -1;
    private volatile String cachedFingerprint;
    private volatile MinioClient cachedClient;

    @Override
    public String presignedPutUrl(String bucket, String objectKey, int expireSeconds) {
        try {
            return client().getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry(expireSeconds)
                    .build());
        } catch (Exception e) {
            log.error("Generate library upload url failed, bucket={}, objectKey={}", bucket, objectKey, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "生成上传地址失败");
        }
    }

    @Override
    public String presignedGetUrl(String bucket, String objectKey, int expireSeconds) {
        try {
            return client().getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry(expireSeconds)
                    .build());
        } catch (Exception e) {
            log.error("Generate library download url failed, bucket={}, objectKey={}", bucket, objectKey, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取下载链接失败");
        }
    }

    @Override
    public Long statSize(String bucket, String objectKey) {
        try {
            return client().statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build()).size();
        } catch (Exception e) {
            log.warn("Stat library object failed, bucket={}, objectKey={}, {}",
                    bucket, objectKey, e.getMessage());
            return null;
        }
    }

    @Override
    public byte[] download(String bucket, String objectKey) {
        try (var stream = client().getObject(io.minio.GetObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .build())) {
            return stream.readAllBytes();
        } catch (Exception e) {
            log.error("Download library object failed, bucket={}, objectKey={}", bucket, objectKey, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "读取资料库文件失败");
        }
    }

    @Override
    public void remove(String bucket, String objectKey) {
        try {
            client().removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            log.warn("Remove library object failed, bucket={}, objectKey={}", bucket, objectKey, e);
        }
    }

    @Override
    public void ensureBucket(String bucket) {
        try {
            MinioClient client = client();
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            log.error("Ensure library bucket failed, bucket={}", bucket, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "资料库存储桶不可用");
        }
    }

    private MinioClient client() {
        if (!credentials.minioEnabled()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "MinIO 未启用，请先在集成设置中配置");
        }
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
}
