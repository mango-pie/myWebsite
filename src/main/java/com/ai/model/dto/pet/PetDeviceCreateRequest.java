package com.ai.model.dto.pet;

import lombok.Data;

@Data
public class PetDeviceCreateRequest {

    private String deviceName;

    /** 设备环境信息，允许传对象或字符串，原样存储 */
    private Object clientInfo;
}
