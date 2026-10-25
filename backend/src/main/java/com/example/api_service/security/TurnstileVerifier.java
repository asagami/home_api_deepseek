package com.example.api_service.security;

import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

/**
 * Cloudflare Turnstile 人机校验服务端校验器。
 *
 * <p>前端在注册/发码页嵌入 Turnstile widget 拿到 token，随请求提交；
 * 本组件携带 secret 调用 siteverify 接口核验 token 真实性与时效性，
 * 阻断脚本直接轰炸发送接口。</p>
 *
 * <p>开关与密钥：</p>
 * <ul>
 *   <li>{@code app.captcha.turnstile.enabled} —— 生产环境必须为 true
 *       （由 {@code SecurityStartupValidator} fail-fast 保证）；</li>
 *   <li>{@code app.captcha.turnstile.secret-key} —— Turnstile 后台签发的服务端密钥
 *       （环境变量 {@code CAPTCHA_TURNSTILE_SECRET_KEY}）。</li>
 * </ul>
 *
 * <p>siteverify 不可达时 fail-closed：人机校验是发送接口的第一道闸门，
 * 故障期间宁可拒绝注册也不放行脚本。</p>
 */
@Slf4j
@Component
public class TurnstileVerifier {

    private static final String SITEVERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final RestClient restClient;
    private final boolean enabled;
    private final String secretKey;

    public TurnstileVerifier(
            @Value("${app.captcha.turnstile.enabled:false}") boolean enabled,
            @Value("${app.captcha.turnstile.secret-key:}") String secretKey) {
        this.enabled = enabled;
        this.secretKey = secretKey;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * 校验前端提交的 Turnstile token。
     *
     * @param token 前端 widget 产生的 token
     * @param ip    客户端 IP，透传给 siteverify 供其附加风控信号
     */
    public void verify(String token, String ip) {
        if (!enabled) {
            // 开发环境跳过；生产环境由 SecurityStartupValidator 保证不在此分支
            return;
        }
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.CAPTCHA_VERIFY_FAILED);
        }
        if (secretKey.isBlank()) {
            // 配置不完整属于部署错误，立即失败而不是默默放行
            log.error("Turnstile 已启用但未配置 secret-key（app.captcha.turnstile.secret-key）");
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "人机校验配置错误，请联系管理员");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secretKey);
        form.add("response", token.trim());
        if (ip != null && !ip.isBlank()) {
            form.add("remoteip", ip);
        }

        SiteVerifyResponse result;
        try {
            result = restClient.post()
                    .uri(SITEVERIFY_URL)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(SiteVerifyResponse.class);
        } catch (Exception ex) {
            // siteverify 不可达时 fail-closed，防止校验环节静默失效
            log.warn("Turnstile siteverify 调用失败，fail-closed 拒绝请求", ex);
            throw new BusinessException(ErrorCode.CAPTCHA_VERIFY_FAILED);
        }
        if (result == null || !result.success()) {
            log.info("Turnstile 校验未通过: errors={}", result == null ? "[]" : result.errorCodes());
            throw new BusinessException(ErrorCode.CAPTCHA_VERIFY_FAILED);
        }
    }

    /**
     * siteverify 响应体，仅映射业务关心的字段；未知字段由 Jackson 忽略。
     */
    record SiteVerifyResponse(
            boolean success,
            @JsonProperty("error-codes") List<String> errorCodes) {
    }
}
