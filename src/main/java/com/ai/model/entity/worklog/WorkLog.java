package com.ai.model.entity.worklog;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Table("work_log")
public class WorkLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    private Long userId;
    private LocalDate workDate;
    private String title;
    private String done;
    private String problem;
    private String summary;
    private String plan;
    /** JSON 数组字符串，如 ["java","refactor"] */
    private String tags;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
