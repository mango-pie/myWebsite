package com.ai.service;

/**
 * 集成凭证读取：DB(integration) 覆盖 YAML/Properties。
 */
public interface IntegrationCredentialsService {

    String knowledgeAiBaseUrl();

    String knowledgeAiApiKey();

    String jinaBaseUrl();

    String jinaApiKey();

    String tavilyBaseUrl();

    String tavilyApiKey();

    String deepseekBaseUrl();

    String deepseekApiKey();

    String agentBaseUrl();

    String agentApiKey();

    String imageCaptionBaseUrl();

    String imageCaptionApiKey();

    String segmentationBaseUrl();

    String segmentationApiKey();

    String astrBotBaseUrl();

    String astrBotApiKey();

    String uploadPath();

    String uploadBaseUrl();

    boolean minioEnabled();

    String minioEndpoint();

    String minioAccessKey();

    String minioSecretKey();

    String minioBucketDocuments();
}
