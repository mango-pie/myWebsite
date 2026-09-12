package com.ai.model.dto.knowledge;

import lombok.Data;

@Data
public class LearningBranchCreateRequest {

    private String title;
    /**
     * 0 = L1。使用 String 接收雪花 ID，避免前端 JSON 大整数精度问题，
     * 以及字符串反序列化失败后落成 null→被当成 0 误建 L1。
     */
    private String parentBranchId;
    private Integer sortOrder;

    /** 解析父枝 id；空 / "0" → 0（L1） */
    public long resolveParentBranchId() {
        if (parentBranchId == null) {
            return 0L;
        }
        String raw = parentBranchId.trim();
        if (raw.isEmpty() || "0".equals(raw) || "null".equalsIgnoreCase(raw)) {
            return 0L;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("parentBranchId 非法: " + parentBranchId);
        }
    }
}
