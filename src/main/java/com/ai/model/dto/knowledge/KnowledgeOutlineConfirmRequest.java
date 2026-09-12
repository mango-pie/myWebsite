package com.ai.model.dto.knowledge;

import lombok.Data;

/**
 * 大纲确认步：用户确认（或修改）后的合蒸大纲。
 */
@Data
public class KnowledgeOutlineConfirmRequest {

    /** 用户确认后的章节骨架；为空则沿用 AI 生成版 */
    private String outline;
}
