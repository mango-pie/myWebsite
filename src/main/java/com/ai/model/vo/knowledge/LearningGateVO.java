package com.ai.model.vo.knowledge;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class LearningGateVO {

    private boolean related;
    private String intent;
    private String reason;
    /** 判定置信度 0.0~1.0；低于阈值时前端展示「可能相关」双选项 */
    private Double confidence;
    /** true 表示用户带理由跳过 AI 判定（人工 override） */
    private boolean overridden;
    private String answer;
    private List<String> hints;
    private String gatePassId;
    private Long suggestedBranchId;
    private String suggestedBranchTitle;
    private Long suggestedParentBranchId;
}
