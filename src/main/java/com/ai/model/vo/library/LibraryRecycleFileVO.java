package com.ai.model.vo.library;

import lombok.Data;

import java.util.List;

@Data
public class LibraryRecycleFileVO {

    private Long id;
    private String name;
    private String ext;
    private String category;
    private Long size;
    private String uploadedAt;
    private List<Long> tagIds;
    private List<Long> collectionIds;
    private String aiStatus;
    private String aiSummary;
    private String sha256;
    private String objectKey;
    /** yyyy-MM-dd，进回收站日期 */
    private String deletedAt;
}
