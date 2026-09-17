package com.ai.model.dto.library;

import lombok.Data;

import java.util.List;

/**
 * 前端当前全量拉取后本地筛选，以下参数为预留的服务端过滤。
 */
@Data
public class LibraryFileQueryRequest {

    private String keyword;

    /** DOCUMENT/IMAGE/AUDIO/VIDEO/CODE/ARCHIVE/OTHER */
    private String category;

    private Long collectionId;

    private List<Long> tagIds;

    /** time/name/size，缺省 time */
    private String sort;

    /** asc/desc，缺省 desc */
    private String dir;
}
