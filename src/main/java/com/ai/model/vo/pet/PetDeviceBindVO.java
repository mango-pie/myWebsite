package com.ai.model.vo.pet;

import java.io.Serializable;
import lombok.Data;

/**
 * 绑定成功时一次性返回明文 token（之后无法再查）。
 */
@Data
public class PetDeviceBindVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String deviceName;

    /** 明文设备令牌，仅本次响应出现 */
    private String token;

    private String tokenPrefix;

    /** 建议桌宠写入的环境变量名 */
    private String envHint = "SITE_DEVICE_TOKEN";
}
