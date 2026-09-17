package com.ai.model.entity.library;

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
@Table("library_file")
public class LibraryFile implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    private Long userId;
    private String fileName;
    private String ext;
    private String category;
    private Long size;
    private String sha256;
    private String objectKey;
    private String uploadStatus;
    private String aiStatus;
    private String aiSummary;
    private String aiError;
    /** JSON 数组字符串 */
    private String tagIds;
    /** JSON 数组字符串 */
    private String collectionIds;
    private LocalDateTime lastOpenedAt;
    private Integer openCount;
    private LocalDate deletedAt;
    private LocalDateTime deleteTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
