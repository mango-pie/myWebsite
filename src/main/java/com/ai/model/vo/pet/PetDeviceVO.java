package com.ai.model.vo.pet;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class PetDeviceVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String deviceName;

    private String clientInfo;

    /** 明文 token 前缀，如 pet_ab12… */
    private String tokenPrefix;

    private LocalDateTime lastUsedAt;

    private LocalDateTime createdTime;

    private boolean revoked;
}
