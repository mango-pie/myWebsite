package com.ai.constant.knowledge;

public final class LearningTreeRedisConstant {

    private LearningTreeRedisConstant() {
    }

    /** gatePass 会话，TTL 与 {@link com.ai.service.knowledge.learning.LearningGatePassStore} 一致 */
    public static final String KEY_GATE_PASS_PREFIX = "learning:gate:";
    public static final long GATE_PASS_TTL_SECONDS = 30 * 60;

    /** 门闩判定结果缓存（domainId + question），避免同一问题重复烧模型 */
    public static final String KEY_GATE_JUDGMENT_PREFIX = "learning:gate:judge:";
    public static final long GATE_JUDGMENT_TTL_SECONDS = 7 * 24 * 3600;

    /** 材料包 URL 缓存（30 天） */
    public static final String KEY_MATERIAL_PREFIX = "learning:material:";
    public static final long MATERIAL_TTL_SECONDS = 30 * 24 * 3600;

    public static String gatePassKey(String gatePassId) {
        return KEY_GATE_PASS_PREFIX + gatePassId;
    }

    public static String gateJudgmentKey(Long domainId, String goal) {
        return KEY_GATE_JUDGMENT_PREFIX + domainId + ":" + md5(goal);
    }

    public static String materialKey(String url) {
        return KEY_MATERIAL_PREFIX + md5(url);
    }

    private static String md5(String value) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
