package com.ai.model.vo.library;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class LibraryFileVO {

    private Long id;
    private String name;
    private String ext;
    private String category;
    private Long size;
    /** yyyy-MM-dd HH:mm */
    private String uploadedAt;
    private List<Long> tagIds = new ArrayList<>();
    private List<Long> collectionIds = new ArrayList<>();
    /** NONE/PENDING/DONE/FAILED */
    private String aiStatus;
    private String aiSummary;
    private String sha256;
    private String objectKey;
    /** 语义搜索匹配分（0~1，仅语义检索接口返回） */
    private Double score;
}
