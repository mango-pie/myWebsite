package com.ai.model.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 桌宠设备令牌（EchoBot 等本地客户端绑定）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("pet_device")
public class PetDevice implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    private Long userId;

    /** SHA-256 hex of raw token；永不回传明文 */
    private String tokenHash;

    private String tokenPrefix;

    private String deviceName;

    private String clientInfo;

    private LocalDateTime lastUsedAt;

    private LocalDateTime revokedAt;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
