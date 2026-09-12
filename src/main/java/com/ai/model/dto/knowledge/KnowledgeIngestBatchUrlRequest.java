package com.ai.model.dto.knowledge;

import lombok.Data;

import java.util.List;

@Data
public class KnowledgeIngestBatchUrlRequest {

    private List<String> urls;
    /** 默认 AGENT */
    private String sourceType = "AGENT";
    private String agentQuery;
    /** 本次附加指令；为空则只用内置精读 Skill（可叠加站点 distill.system_prompt） */
    private String distillPrompt;
    private String tags;
    /** true 时先 flash 生成合蒸大纲，用户确认后才开始蒸馏（结构性防返工） */
    private Boolean outlineConfirm;
    /** 用户确认后的合蒸大纲（章节骨架），蒸馏时对齐输出 */
    private String outline;
    /** 复用搜索阶段已创建的 Job（须为 AWAITING_SELECT） */
    private Long jobId;
    /** V3 树模式 */
    private Long domainId;
    private String gatePassId;
    private Long branchId;
    private Boolean skipGate;
}
