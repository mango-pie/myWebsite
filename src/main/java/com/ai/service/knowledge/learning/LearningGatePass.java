package com.ai.service.knowledge.learning;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearningGatePass {

    private String gatePassId;
    private Long userId;
    private Long domainId;
    private boolean related;
    private String intent;
    /** 判定置信度 0.0~1.0 */
    private Double confidence;
    /** true 表示用户带理由跳过 AI 判定 */
    private boolean overridden;
    private String overrideReason;
    private String goal;
    private Long suggestedBranchId;
    private String suggestedBranchTitle;
    private Long suggestedParentBranchId;
    private Instant expiresAt;
}
