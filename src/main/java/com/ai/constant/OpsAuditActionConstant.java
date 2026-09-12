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
    public static final String READING_NOTE_REDISTILL = "reading.note.redistill";
    public static final String READING_NOTE_PUBLISH = "reading.note.publish";
    public static final String READING_NOTE_SYNC = "reading.note.sync";
    public static final String READING_NOTE_INDEX = "reading.note.index";
    public static final String READING_NOTE_REINDEX = "reading.note.reindex";
    public static final String READING_NOTE_ARCHIVE = "reading.note.archive";
    public static final String READING_NOTE_REVIEW = "reading.note.review";
    public static final String LEARNING_GATE = "learning.gate";
    public static final String LEARNING_GATE_MISTAKE = "learning.gate.mistake";
    public static final String LEARNING_LEAF_ATTACH = "learning.leaf.attach";
    public static final String LEARNING_LEAF_MOVE = "learning.leaf.move";
    public static final String LEARNING_LEAF_DETACH = "learning.leaf.detach";
    public static final String LEARNING_BRANCH_MERGE = "learning.branch.merge";

    public static final String RESOURCE_USER = "user";
    public static final String RESOURCE_SITE = "site";
    public static final String RESOURCE_APP = "app";
    public static final String RESOURCE_KNOWLEDGE_BASE = "knowledge_base";
    public static final String RESOURCE_KNOWLEDGE_DOCUMENT = "knowledge_document";
    public static final String RESOURCE_READING_NOTE = "reading_note";
    public static final String RESOURCE_READING_JOB = "reading_job";
    public static final String RESOURCE_LEARNING_DOMAIN = "learning_domain";
    public static final String RESOURCE_LEARNING_BRANCH = "learning_branch";
    public static final String RESOURCE_LEARNING_LEAF = "learning_leaf";

    private OpsAuditActionConstant() {
    }
}
