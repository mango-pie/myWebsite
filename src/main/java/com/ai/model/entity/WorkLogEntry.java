package com.ai.model.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 每日工作日志（与日记表完全独立）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("worklog_entry")
public class WorkLogEntry implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    private Long userId;

    private LocalDate workDate;

    private String title;

    private String doneText;

    private String problemText;

    private String summaryText;

    private String planText;

    /** JSON 数组字符串 */
    private String tags;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;

    private LocalDateTime deletedTime;
}
