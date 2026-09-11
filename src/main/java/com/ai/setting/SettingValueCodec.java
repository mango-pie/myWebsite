package com.ai.setting;

import com.ai.constant.SiteSettingConstant;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;

/**
 * 设置值与字符串互转。
 */
public final class SettingValueCodec {

    private SettingValueCodec() {
    }

    public static String encode(Object value, String valueType) {
        if (value == null) {
            return null;
        }
        return switch (normalizeType(valueType)) {
            case SiteSettingConstant.TYPE_BOOL -> String.valueOf(toBool(value));
            case SiteSettingConstant.TYPE_INT, SiteSettingConstant.TYPE_LONG -> String.valueOf(toLong(value));
            case SiteSettingConstant.TYPE_DOUBLE -> String.valueOf(toDouble(value));
            default -> String.valueOf(value);
        };
    }

    public static Object decode(String raw, String valueType) {
        if (raw == null) {
            return null;
        }
        return switch (normalizeType(valueType)) {
            case SiteSettingConstant.TYPE_BOOL -> Boolean.parseBoolean(raw);
            case SiteSettingConstant.TYPE_INT -> Integer.parseInt(raw);
            case SiteSettingConstant.TYPE_LONG -> Long.parseLong(raw);
            case SiteSettingConstant.TYPE_DOUBLE -> Double.parseDouble(raw);
            default -> raw;
        };
    }

    public static boolean toBool(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        String s = String.valueOf(value).trim().toLowerCase();
        if ("true".equals(s) || "1".equals(s) || "yes".equals(s)) {
            return true;
        }
        if ("false".equals(s) || "0".equals(s) || "no".equals(s)) {
            return false;
        }
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "非法布尔值: " + value);
    }

    public static long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "非法数字: " + value);
        }
    }

    public static double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "非法小数: " + value);
        }
    }

    private static String normalizeType(String valueType) {
        return valueType == null ? SiteSettingConstant.TYPE_STRING : valueType.toLowerCase();
    }
}
