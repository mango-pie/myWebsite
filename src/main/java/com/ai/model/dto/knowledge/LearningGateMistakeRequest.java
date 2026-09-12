package com.ai.model.dto.knowledge;

import lombok.Data;

/**
 * 门闩判定错误回写（数据闭环）。
 */
@Data
public class LearningGateMistakeRequest {

    private String reason;
}
