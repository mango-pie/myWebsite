package com.ai.model.vo.knowledge;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class KnowledgeDownloadUrlVO {

    private String url;
    private Integer expireSeconds;
}
