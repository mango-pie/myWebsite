package com.ai.constant.knowledge;

public final class KnowledgeNoteConstant {

    private KnowledgeNoteConstant() {
    }

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_DELETED = "DELETED";

    public static final String PUBLISH_NOT_PUBLISHED = "NOT_PUBLISHED";
    public static final String PUBLISH_DRAFT_CREATED = "DRAFT_CREATED";
    public static final String PUBLISH_PUBLISHED = "PUBLISHED";
    public static final String PUBLISH_SYNC_REQUIRED = "SYNC_REQUIRED";
    public static final String PUBLISH_SYNC_FAILED = "SYNC_FAILED";

    public static final String INDEX_NOT_INDEXED = "NOT_INDEXED";
    public static final String INDEX_INDEXED = "INDEXED";
    public static final String INDEX_REINDEX_REQUIRED = "REINDEX_REQUIRED";
    public static final String INDEX_FAILED = "INDEX_FAILED";

    public static final String SOURCE_URL = "URL";
    public static final String SOURCE_FILE = "FILE";
    public static final String SOURCE_AGENT = "AGENT";

    public static final String VIRTUAL_BUCKET = "knowledge-notes";
    public static final String FILE_TYPE_MD = "MD";
}
