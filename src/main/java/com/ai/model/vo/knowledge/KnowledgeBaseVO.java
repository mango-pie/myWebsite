package com.ai.model.vo.knowledge;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeBaseVO {

    private Long id;
    private String name;
    private String description;
    private Long userId;
    private String visibility;
    private Integer status;
    private Integer documentCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
