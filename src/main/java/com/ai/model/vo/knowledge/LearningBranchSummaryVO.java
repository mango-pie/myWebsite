package com.ai.model.vo.knowledge;

import lombok.Data;

/**
 * 枝级综述：某枝下多篇 note 聚合生成的知识地图 / 综述。
 */
@Data
public class LearningBranchSummaryVO {

    private Long branchId;
    private String branchTitle;
    private String summaryMd;
}
