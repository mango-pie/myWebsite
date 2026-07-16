package com.ai.constant;

import java.util.Set;

/**
 * HTTP 访问日志模式（ops-observability Phase D）。
 */
public final class HttpLogModeConstant {

    public static final String ERRORS_ONLY = "errors_only";
    public static final String SLOW_AND_ERRORS = "slow_and_errors";
    public static final String ALL = "all";

    public static final Set<String> ALL_MODES = Set.of(ERRORS_ONLY, SLOW_AND_ERRORS, ALL);

    private HttpLogModeConstant() {
    }

    public static boolean isKnown(String mode) {
        return mode != null && ALL_MODES.contains(mode);
    }
}
