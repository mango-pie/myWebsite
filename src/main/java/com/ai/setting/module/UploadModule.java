package com.ai.setting.module;

import cn.hutool.core.util.StrUtil;
import com.ai.constant.SiteSettingConstant;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.setting.SettingFieldSchema;
import com.ai.setting.SettingModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class UploadModule implements SettingModule {

    @Value("${upload.max-file-size:20}")
    private long defaultMaxFileSizeMb;

    @Override
    public String code() {
        return SiteSettingConstant.MODULE_UPLOAD;
    }

    @Override
    public String displayName() {
        return "上传";
    }

    @Override
    public String phase() {
        return "P0";
    }

    @Override
    public List<SettingFieldSchema> schema() {
        return List.of(
                SettingFieldSchema.builder()
                        .key("max_file_size_mb")
                        .valueType(SiteSettingConstant.TYPE_INT)
                        .label("最大文件大小(MB)")
                        .description("业务上传上限，不超过 Spring multipart 50MB")
                        .defaultValue((int) defaultMaxFileSizeMb)
                        .min(1.0)
                        .max(50.0)
                        .build(),
                SettingFieldSchema.builder()
                        .key("allowed_image_ext")
                        .valueType(SiteSettingConstant.TYPE_STRING)
                        .label("允许的图片扩展名")
                        .description("逗号分隔，不含点号")
                        .defaultValue("jpg,jpeg,png,gif,webp")
                        .build(),
                SettingFieldSchema.builder()
                        .key("presign_expire_seconds")
                        .valueType(SiteSettingConstant.TYPE_INT)
                        .label("预签名过期秒数")
                        .description("知识库下载预签名 URL 过期时间")
                        .defaultValue(3600)
                        .min(60.0)
                        .max(86400.0)
                        .build()
        );
    }

    @Override
    public Map<String, Object> defaults() {
        Map<String, Object> map = new LinkedHashMap<>();
        for (SettingFieldSchema field : schema()) {
            map.put(field.getKey(), field.getDefaultValue());
        }
        return map;
    }

    @Override
    public void validate(Map<String, Object> items) {
        if (items.containsKey("max_file_size_mb")) {
            int size = toInt(items.get("max_file_size_mb"), "max_file_size_mb");
            if (size < 1 || size > 50) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "max_file_size_mb 须在 1～50 之间");
            }
        }
        if (items.containsKey("allowed_image_ext")) {
            String ext = String.valueOf(items.get("allowed_image_ext"));
            if (StrUtil.isBlank(ext)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "allowed_image_ext 不能为空");
            }
            for (String part : ext.split(",")) {
                String e = part.trim().toLowerCase();
                if (e.isEmpty() || e.contains(".") || e.contains("/") || e.contains("\\")) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "非法扩展名: " + part);
                }
            }
        }
        if (items.containsKey("presign_expire_seconds")) {
            int sec = toInt(items.get("presign_expire_seconds"), "presign_expire_seconds");
            if (sec < 60 || sec > 86400) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "presign_expire_seconds 须在 60～86400 之间");
            }
        }
    }

    private int toInt(Object value, String key) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, key + " 必须是整数");
        }
    }
}
