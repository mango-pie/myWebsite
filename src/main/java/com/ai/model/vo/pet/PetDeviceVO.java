package com.ai.model.vo.pet;

import lombok.Data;

@Data
public class PetDeviceVO {

    private Long id;
    private String deviceName;
    private Object clientInfo;
    private String status;
    /** yyyy-MM-dd HH:mm */
    private String boundAt;
    private String revokedAt;

    /** 明文 token，仅在绑定创建时返回一次 */
    private String token;
}
