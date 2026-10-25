package com.example.api_service.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * 客户端 IP 解析工具，供各拦截器、控制器、日志落库共用。
 *
 * <p>历史上本类无条件信任 {@code X-Forwarded-For} / {@code X-Real-IP} 首段，任意调用方
 * 都能伪造请求头进而污染落库的 {@code client_ip}，让基于 IP 的封禁、风控与纠纷举证
 * 失去证据效力。修复策略：</p>
 *
 * <ol>
 *   <li><b>默认不信任任何 forwarded 头</b>：仅当运维显式声明应用部署在可信反向代理之后
 *       （{@code app.security.trust-forwarded-headers=true}，并配套开启
 *       {@code server.forward-headers-strategy=NATIVE}）时才解析转发头；
 *       否则一律使用 {@link HttpServletRequest#getRemoteAddr()}。</li>
 *   <li><b>校验 IP 格式</b>：仅接受合法的 IPv4 / IPv6 文本；非法值直接丢弃并回退到
 *       {@code getRemoteAddr()}，杜绝脏数据写库与日志注入。</li>
 *   <li><b>逐段校验</b>：X-Forwarded-For 按逗号拆分后逐段校验，跳过任何非法段，
 *       避免攻击者用伪造的脏值覆盖真实客户端 IP。</li>
 * </ol>
 *
 * <p>本类是 Spring Bean，通过 {@code @Value} 注入信任开关；调用方注入实例使用。</p>
 */
@Slf4j
@Component
public class ClientIpUtils {

    private static final Pattern IPv4 =
            Pattern.compile("^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");
    /** IPv6 文本（含 :: 缩写与 IPv4-mapped 形式）的宽松校验，宁可放过也不误杀合法地址。 */
    private static final Pattern IPv6 = Pattern.compile(
            "^(([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}"
            + "|([0-9a-fA-F]{1,4}::){1,6}[0-9a-fA-F]{1,4}"
            + "|([0-9a-fA-F]{1,4}::){1,5}(:[0-9a-fA-F]{1,4}){1,2}"
            + "|([0-9a-fA-F]{1,4}::){1,4}(:[0-9a-fA-F]{1,4}){1,3}"
            + "|([0-9a-fA-F]{1,4}::){1,3}(:[0-9a-fA-F]{1,4}){1,4}"
            + "|([0-9a-fA-F]{1,4}::){1,2}(:[0-9a-fA-F]{1,4}){1,5}"
            + "|[0-9a-fA-F]{1,4}::(:[0-9a-fA-F]{1,4}){1,6}"
            + "|([0-9a-fA-F]{1,4}:){1,7}:"
            + "|::([fF]{4}:)((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d))$");

    /** 运维声明应用部署在可信反向代理之后时才解析转发头，默认关闭。 */
    @Value("${app.security.trust-forwarded-headers:false}")
    private boolean trustForwardedHeaders;

    public String resolve(HttpServletRequest request) {
        if (trustForwardedHeaders) {
            String fromHeader = pickFirstValidFromForwarded(request);
            if (fromHeader != null) {
                return fromHeader;
            }
        }
        return sanitize(request.getRemoteAddr());
    }

    /**
     * 在转发头中寻找首个合法 IP。
     *
     * <p>优先 X-Forwarded-For（按逗号拆分逐段校验），其次 X-Real-IP。
     * 任一不合法段直接跳过，避免攻击者用伪造的脏值覆盖真实客户端 IP。</p>
     */
    private String pickFirstValidFromForwarded(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            for (String segment : forwardedFor.split(",")) {
                String candidate = sanitize(segment);
                if (candidate != null) {
                    return candidate;
                }
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return sanitize(realIp.trim());
        }
        return null;
    }

    /**
     * 校验并归一化 IP 文本。
     *
     * <p>仅接受合法 IPv4 / IPv6；非法输入返回 {@code null}，由调用方回退到
     * {@code getRemoteAddr()}。同时去括号与端口后缀（如 {@code [::1]:8080}）。</p>
     */
    private String sanitize(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        // 去掉 IPv6 字面量的方括号与可选端口
        if (value.startsWith("[") && value.contains("]")) {
            value = value.substring(1, value.indexOf(']'));
        } else if (value.indexOf(':') != value.lastIndexOf(':')
                && value.contains(":") && !value.contains("::")) {
            // 形如 1.2.3.4:8080 的带端口 IPv4
            int colon = value.lastIndexOf(':');
            value = value.substring(0, colon);
        }
        if (IPv4.matcher(value).matches() || IPv6.matcher(value).matches()) {
            return value;
        }
        log.debug("丢弃非法 client_ip 值: raw={}", raw);
        return null;
    }
}
