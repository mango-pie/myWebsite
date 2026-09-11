package com.ai.constant;

/**
 * 通用操作审计 action（ops-observability Phase B）。
 */
public final class OpsAuditActionConstant {

    public static final String USER_LOGIN_SUCCESS = "user.login.success";
    public static final String USER_LOGIN_FAIL = "user.login.fail";
    public static final String USER_LOGOUT = "user.logout";
    public static final String SITE_MAINTENANCE_TOGGLE = "site.maintenance.toggle";
    public static final String APP_DEPLOY = "app.deploy";
    public static final String APP_DELETE = "app.delete";
    public static final String KNOWLEDGE_BASE_DELETE = "knowledge.base.delete";
    public static final String KNOWLEDGE_DOCUMENT_DELETE = "knowledge.document.delete";
    public static final String READING_NOTE_DELETE = "reading.note.delete";

    public static final String RESOURCE_USER = "user";
    public static final String RESOURCE_SITE = "site";
    public static final String RESOURCE_APP = "app";
    public static final String RESOURCE_KNOWLEDGE_BASE = "knowledge_base";
    public static final String RESOURCE_KNOWLEDGE_DOCUMENT = "knowledge_document";
    public static final String RESOURCE_READING_NOTE = "reading_note";

    private OpsAuditActionConstant() {
    }
}
