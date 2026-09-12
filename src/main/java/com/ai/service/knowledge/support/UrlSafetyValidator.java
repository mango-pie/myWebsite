package com.ai.service.knowledge.support;

import com.ai.config.ConditionalOnModule;

import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;

/**
 * SSRF 防护（评审 §5）：协议白名单 + 私有网段 / 回环 IP 校验 + 主机名黑名单。
 * 用于服务器直连抓取（如 GitHub raw），避免把内网地址交给 HttpClient。
 */
@ConditionalOnModule("knowledge")
@Component
public class UrlSafetyValidator {

    private static final List<String> BLOCKED_HOSTS = List.of(
            "localhost", "metadata.google.internal", "metadata.azure.internal", "169.254.169.254");

    /** 协议白名单：所有抓取入口统一校验 */
    public void checkProtocol(String url) {
        if (url == null || url.isBlank()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "URL 不能为空");
        }
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "仅允许 http/https 协议抓取");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "URL 缺少合法主机名");
            }
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "URL 格式不合法");
        }
    }

    /** 服务器直连前的强校验：协议 + 主机 + DNS 解析出的全部 IP 均须为公网 */
    public void checkDirectFetch(String url) {
        checkProtocol(url);
        try {
            URI uri = URI.create(url.trim());
            String host = uri.getHost();
            String lower = host.toLowerCase(Locale.ROOT);
            if (BLOCKED_HOSTS.contains(lower) || lower.endsWith(".local") || lower.endsWith(".internal")) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "目标主机被禁止抓取");
            }
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (isPrivateOrUnsafe(address)) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR,
                            "目标地址解析到私有网段，已拦截（" + address.getHostAddress() + "）");
                }
            }
        } catch (UnknownHostException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "目标域名解析失败");
        }
    }

    private boolean isPrivateOrUnsafe(InetAddress address) {
        if (address == null) {
            return true;
        }
        if (address.isLoopbackAddress() || address.isSiteLocalAddress()
                || address.isLinkLocalAddress() || address.isAnyLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] raw = address.getAddress();
        if (raw == null) {
            return true;
        }
        if (raw.length == 4) {
            int first = raw[0] & 0xFF;
            int second = raw[1] & 0xFF;
            // 0.0.0.0/8, 100.64.0.0/10 (CGNAT), 169.254.0.0/16, 198.18.0.0/15
            if (first == 0 || (first == 100 && (second & 0xC0) == 0x40)
                    || (first == 169 && second == 254) || (first == 198 && (second & 0xFE) == 18)) {
                return true;
            }
        } else if (raw.length == 16) {
            // ::1, ::, fe80::/10, fc00::/7
            boolean v6Loopback = true;
            for (int i = 0; i < 15; i++) {
                if (raw[i] != 0) {
                    v6Loopback = false;
                    break;
                }
            }
            if (v6Loopback && raw[15] == 1) {
                return true;
            }
            int first = raw[0] & 0xFF;
            if ((first & 0xFE) == 0xFC || ((first & 0xFF) == 0xFE && (raw[1] & 0xC0) == 0x80)) {
                return true;
            }
            boolean unspecified = true;
            for (byte b : raw) {
                if (b != 0) {
                    unspecified = false;
                    break;
                }
            }
            if (unspecified) {
                return true;
            }
        }
        return false;
    }
}
