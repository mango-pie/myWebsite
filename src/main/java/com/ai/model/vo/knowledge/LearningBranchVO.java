package com.ai.model.vo.knowledge;

import lombok.Data;

import java.util.List;

@Data
public class LearningBranchVO {

    private Long id;
    private Long domainId;
    private Long parentBranchId;
    private Integer depth;
    private String title;
    private Integer sortOrder;
    private int leafCount;
    /** 枝内已掌握 note 数（复习状态聚合） */
    private int masteredCount;
    /** 枝内掌握度百分比 0~100 */
    private int masteryPercent;
    private String path;
    /** includeLeaves=true 时填充 */
    private java.util.List<LearningLeafVO> leaves;
}
