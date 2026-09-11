package com.ai.service.knowledge.support;

import cn.hutool.core.util.StrUtil;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 候选 URL 风险标记与视频域名判定（主题无关）。
 */
public final class SearchCandidateRiskHelper {

    public static final String VIDEO_HOST = "VIDEO_HOST";
    public static final String COURSE_LANDING = "COURSE_LANDING";
    public static final String PAYWALL_LIKELY = "PAYWALL_LIKELY";

    private static final Set<String> COURSE_HINTS = Set.of(
            "imooc.com", "coding.imooc.com", "educoder.net", "class.coursera.org",
            "udemy.com", "lanqiao.cn"
    );

    private static final Set<String> PAYWALL_HINTS = Set.of(
            "zhihu.com", "zhuanlan.zhihu.com", "mp.weixin.qq.com"
    );

    private SearchCandidateRiskHelper() {
    }

    public static String hostOf(String url) {
        if (StrUtil.isBlank(url)) {
            return "";
        }
        try {
            String host = URI.create(url.trim()).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return "";
        }
    }

    public static boolean matchesDomainList(String url, List<String> domains) {
        String host = hostOf(url);
        if (host.isEmpty() || domains == null) {
            return false;
        }
        for (String domain : domains) {
            if (StrUtil.isBlank(domain)) {
                continue;
            }
            String d = domain.trim().toLowerCase(Locale.ROOT);
            if (host.equals(d) || host.endsWith("." + d)) {
                return true;
            }
        }
        return false;
    }

    public static List<String> riskFlags(String url) {
        List<String> flags = new ArrayList<>();
        String host = hostOf(url);
        if (host.isEmpty()) {
            return flags;
        }
        for (String hint : COURSE_HINTS) {
            if (host.equals(hint) || host.endsWith("." + hint)) {
                flags.add(COURSE_LANDING);
                break;
            }
        }
        for (String hint : PAYWALL_HINTS) {
            if (host.equals(hint) || host.endsWith("." + hint)) {
                flags.add(PAYWALL_LIKELY);
                break;
            }
        }
        return flags;
    }
}
