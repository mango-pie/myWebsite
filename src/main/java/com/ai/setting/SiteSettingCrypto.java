package com.ai.setting;

import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 设置敏感字段 AES-GCM 加解密。主密钥来自 SITE_SETTING_CRYPTO_SECRET / site-setting.crypto-secret。
 */
@Slf4j
@Component
public class SiteSettingCrypto {

    public static final String ENC_PREFIX = "ENC:";

    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public SiteSettingCrypto(
            @Value("${site-setting.crypto-secret:${SITE_SETTING_CRYPTO_SECRET:}}") String cryptoSecret) {
        String secret = cryptoSecret == null ? "" : cryptoSecret.trim();
        if (secret.isEmpty()) {
            log.warn("site-setting.crypto-secret / SITE_SETTING_CRYPTO_SECRET 未配置，使用本地开发默认密钥（请勿用于生产）");
            secret = "ai-backend-dev-only-site-setting-crypto";
        }
        this.secretKey = deriveKey(secret);
    }

    public String encrypt(String plain) {
        if (plain == null) {
            return null;
        }
        if (plain.startsWith(ENC_PREFIX)) {
            return plain;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] cipherBytes = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherBytes.length);
            buffer.put(iv);
            buffer.put(cipherBytes);
            return ENC_PREFIX + Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "敏感配置加密失败");
        }
    }

    public String decrypt(String stored) {
        if (stored == null || stored.isBlank()) {
            return stored;
        }
        if (!stored.startsWith(ENC_PREFIX)) {
            // 兼容未加密历史数据
            return stored;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(stored.substring(ENC_PREFIX.length()));
            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] cipherBytes = new byte[buffer.remaining()];
            buffer.get(cipherBytes);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] plain = cipher.doFinal(cipherBytes);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "敏感配置解密失败，请检查 crypto-secret 是否与写入时一致");
        }
    }

    public boolean isEncrypted(String stored) {
        return stored != null && stored.startsWith(ENC_PREFIX);
    }

    private static SecretKey deriveKey(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive site-setting crypto key", e);
        }
    }
}
