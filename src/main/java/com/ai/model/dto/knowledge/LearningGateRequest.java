package com.ai.model.dto.knowledge;

import lombok.Data;

@Data
public class LearningGateRequest {

    private Long domainId;
    private String question;
    /** 带理由跳过：用户指定意图（LEAF/BRANCH/CHAT_ONLY），绕过 AI 判定签发 override pass */
    private String overrideIntent;
    /** 带理由跳过：必填的人工理由 */
    private String overrideReason;
}
