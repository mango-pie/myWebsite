package com.ai.model.vo.knowledge;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LearningDomainVO {

    private Long id;
    private String name;
    private Integer sortOrder;
    private LocalDateTime createTime;
}
