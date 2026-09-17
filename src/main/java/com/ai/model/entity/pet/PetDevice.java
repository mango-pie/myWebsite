package com.ai.model.entity.pet;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Table("pet_device")
public class PetDevice implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    private Long userId;
    private String deviceName;
    private String clientInfo;
    private String tokenHash;
    private String status;
    private LocalDateTime boundAt;
    private LocalDateTime revokedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
