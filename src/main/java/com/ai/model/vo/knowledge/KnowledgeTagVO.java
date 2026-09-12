package com.ai.model.vo.knowledge;

import lombok.Data;

/**
 * 标签统计（标签管理：合并 / 重命名 / 删除）。
 */
@Data
public class KnowledgeTagVO {

    private String name;
    private long count;
}
