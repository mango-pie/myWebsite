package com.ai.constant;

/**
 * 桌宠 / EchoBot 设备鉴权常量。
 */
public interface PetAuthConstant {

    /** 原始 token 在请求头中的形式：Authorization: Bearer pet_xxx */
    String BEARER_PREFIX = "Bearer ";

    /** Filter 解析成功后挂到 request 上的 User */
    String PET_LOGIN_USER_ATTR = "pet.login.user";

    /** 设备记录 ID（可选，供审计） */
    String PET_DEVICE_ID_ATTR = "pet.device.id";

    String TOKEN_PREFIX = "pet_";
}
