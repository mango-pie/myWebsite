package com.ai.model.vo.knowledge;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LearningLeafVO {

    private Long id;
    private Long branchId;
    private Long noteId;
    private String noteTitle;
    private String publishStatus;
    private String indexStatus;
    /** 复习状态 NEW / REVIEWING / MASTERED */
    private String reviewStatus;
    private LocalDateTime attachedAt;
}
