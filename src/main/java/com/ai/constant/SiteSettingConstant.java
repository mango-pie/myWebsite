package com.ai.constant;

/**
 * 全站设置常量。
 */
public interface SiteSettingConstant {

    String CACHE_KEY_PREFIX = "site_setting:";

    long CACHE_TTL_SECONDS = 300L;

    String SOURCE_DB = "db";
    String SOURCE_DEFAULT = "default";

    String MODULE_SITE = "site";
    String MODULE_SECURITY = "security";
    String MODULE_UPLOAD = "upload";
    String MODULE_INTEGRATION = "integration";
    /** 业务模块开关，key 与 ModuleEnablementService.snapshot() 一致 */
    String MODULE_MODULES = "modules";
    String MODULE_CHAT = "chat";
    String MODULE_TTS = "tts";
    String MODULE_KNOWLEDGE = "knowledge";
    String MODULE_READING = "reading";
    String MODULE_BLOG = "blog";
    String MODULE_STUDY = "study";
    String MODULE_DIARY = "diary";
    String MODULE_APP = "app";
    String MODULE_OPS = "ops";

    String TYPE_STRING = "string";
    String TYPE_INT = "int";
    String TYPE_LONG = "long";
    String TYPE_BOOL = "bool";
    String TYPE_DOUBLE = "double";
    String TYPE_JSON = "json";
}
