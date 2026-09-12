package com.ai.model.dto.pet;

import java.io.Serializable;
import lombok.Data;

@Data
public class PetDeviceBindRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 设备显示名，默认 EchoBot */
    private String deviceName;

    /** 可选：OS / 版本等 */
    private String clientInfo;
}
