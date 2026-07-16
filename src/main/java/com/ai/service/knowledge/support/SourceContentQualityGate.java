package com.ai.service.knowledge.support;

import cn.hutool.core.util.StrUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 抓取正文质量门禁（主题无关）：墙 / 过短 / 视频壳 / 无关 / 薄内容。
 */
public final class SourceContentQualityGate {

    public static final String CAPTCHA = "CAPTCHA";
    public static final String TOO_SHORT = "TOO_SHORT";
    public static final String VIDEO_SHELL = "VIDEO_SHELL";
    public static final String IRRELEVANT = "IRRELEVANT";
    public static final String TOO_THIN = "TOO_THIN";

    private static final int MIN_CHARS = 800;

    private static final Pattern CAPTCHA_PATTERN = Pattern.compile(
            "安全验证|验证码|请您登录|请登录|登录后查看|CAPTCHA|cloudflare|异常访问|访问受限|人机验证",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern VIDEO_SHELL_PATTERN = Pattern.compile(
            "订阅者|观看量|次观看|views?\\b|playlist|播放列表|哔哩哔哩|抖音|打开 APP|点击播放",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern THIN_LANDING_PATTERN = Pattern.compile(
            "课程涵盖|配套资源|实战课程|从入门到|立即报名|立即学习|限时优惠|智慧课程|教学课堂|人才培养",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern CODE_FENCE = Pattern.compile("```");

    private static final Set<String> STOP_WORDS = Set.of(
            "想", "学", "学习", "一下", "如何", "怎么", "的", "了", "和", "与", "及", "在", "我",
            "要", "会", "请", "帮", "关于", "入门", "进阶", "基础", "实践", "目标", "主题"
    );

    private SourceContentQualityGate() {
    }

    public record Reject(String reasonCode, String message) {
    }

    /**
     * @return null 表示通过；否则为拒绝原因
     */
    public static Reject evaluate(String rawText, String url, String goalOrQuery) {
        String text = rawText == null ? "" : rawText.trim();
        if (CAPTCHA_PATTERN.matcher(text).find()) {
            return new Reject(CAPTCHA, "页面疑似登录墙或验证码，无法作为文章来源");
        }
        if (text.length() < MIN_CHARS) {
            return new Reject(TOO_SHORT, "正文过短（<" + MIN_CHARS + " 字），不像完整技术文章");
        }
        if (isVideoShell(text, url)) {
            return new Reject(VIDEO_SHELL, "内容像视频/播放器壳页，不是文章正文");
        }
        if (isIrrelevant(text, goalOrQuery)) {
            return new Reject(IRRELEVANT, "正文与学习目标关键词几乎无关");
        }
        if (isTooThin(text)) {
            return new Reject(TOO_THIN, "内容过薄（课程售卖/平台介绍类），缺少可精读段落");
        }
        return null;
    }

    private static boolean isVideoShell(String text, String url) {
        if (SearchCandidateRiskHelper.matchesDomainList(url, List.of(
                "youtube.com", "youtu.be", "bilibili.com", "b23.tv", "vimeo.com",
                "tiktok.com", "douyin.com", "ixigua.com", "youku.com", "iqiyi.com"))) {
            return true;
        }
        int hits = countMatches(VIDEO_SHELL_PATTERN, text);
        return hits >= 3 && !CODE_FENCE.matcher(text).find() && text.length() < 4000;
    }

    private static boolean isTooThin(String text) {
        int landingHits = countMatches(THIN_LANDING_PATTERN, text);
        boolean hasCode = CODE_FENCE.matcher(text).find();
        long paragraphLike = text.lines()
                .map(String::trim)
                .filter(l -> l.length() >= 40)
                .count();
        if (landingHits >= 3 && !hasCode && paragraphLike < 8) {
            return true;
        }
        return landingHits >= 5 && !hasCode;
    }

    private static boolean isIrrelevant(String text, String goalOrQuery) {
        List<String> keywords = extractKeywords(goalOrQuery);
        if (keywords.isEmpty()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        // 去掉空格/连字符后再比，避免「Springboot3」对不上「Spring Boot」
        String compact = lower.replaceAll("[^a-z0-9\\u4e00-\\u9fff]+", "");
        int hit = 0;
        for (String kw : keywords) {
            if (keywordMatches(lower, compact, kw)) {
                hit++;
            }
        }
        // 关键词至少一个命中则认为相关；全无命中才拒
        return hit == 0;
    }

    /**
     * 字面包含，或去掉分隔符/尾部版本号后的紧凑匹配（Springboot3 ↔ Spring Boot）。
     */
    static boolean keywordMatches(String lowerText, String compactText, String kw) {
        if (StrUtil.isBlank(kw)) {
            return false;
        }
        String k = kw.toLowerCase(Locale.ROOT);
        if (lowerText.contains(k)) {
            return true;
        }
        String compactKw = k.replaceAll("[^a-z0-9\\u4e00-\\u9fff]+", "");
        if (compactKw.length() >= 2 && compactText.contains(compactKw)) {
            return true;
        }
        // Springboot3 / JDK17 → 去掉尾部数字再匹配
        String noVer = compactKw.replaceFirst("\\d+$", "");
        return noVer.length() >= 4 && compactText.contains(noVer);
    }

    public static List<String> extractKeywords(String goalOrQuery) {
        if (StrUtil.isBlank(goalOrQuery)) {
            return List.of();
        }
        String cleaned = goalOrQuery.replaceAll("[\\p{Punct}，。！？、；：\"\"''（）【】《》\\s]+", " ").trim();
        List<String> keywords = new ArrayList<>();
        for (String token : cleaned.split("\\s+")) {
            if (token.length() < 2) {
                continue;
            }
            if (STOP_WORDS.contains(token.toLowerCase(Locale.ROOT)) || STOP_WORDS.contains(token)) {
                continue;
            }
            // 英文词（含 Springboot3 这类粘连）
            if (token.matches("[A-Za-z][A-Za-z0-9+.#-]{1,}")) {
                keywords.add(token);
                String noVer = token.replaceFirst("\\d+$", "");
                if (noVer.length() >= 4 && !noVer.equals(token)) {
                    keywords.add(noVer);
                }
                continue;
            }
            // 中文：长度 >= 2 的连续片段
            if (token.matches("[\\u4e00-\\u9fff]{2,}")) {
                keywords.add(token);
                // 也尝试 2 字滑窗以兜住「SpringSecurity」类粘连较少的中文
                if (token.length() >= 4) {
                    for (int i = 0; i + 2 <= token.length(); i++) {
                        String bi = token.substring(i, i + 2);
                        if (!STOP_WORDS.contains(bi)) {
                            keywords.add(bi);
                        }
                    }
                }
            }
        }
        return keywords.stream().distinct().limit(12).toList();
    }

    private static int countMatches(Pattern pattern, String text) {
        var m = pattern.matcher(text);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }
}
