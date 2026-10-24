package com.example.api_service.engine.handlers.geo;

import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;
import com.example.api_service.engine.AbstractHttpProxyApiHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/**
 * HTTP 第三方转发标杆示例：IP 归属地查询。
 *
 * <p>上游地址通过配置注入，默认使用无需密钥的 ipapi.co；连接 2s、读取 5s
 * 超时由 {@link AbstractHttpProxyApiHandler} 统一设置。请求不继承
 * BasePageRequest，因此计费倍率固定为 1。</p>
 */
@Component
public class IpGeoLocateApiHandler
        extends AbstractHttpProxyApiHandler<IpLocateReq, IpLocateVO> {

    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public IpGeoLocateApiHandler(
            ObjectMapper objectMapper,
            @Value("${app.proxy.ip-geo.base-url:https://ipapi.co}") String baseUrl) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper 不能为空");
        this.baseUrl = stripTrailingSlash(baseUrl);
    }

    @Override
    public String getEndpointPath() {
        return "/api/v1/tools/ip-locate";
    }

    @Override
    protected String buildTargetUrl(IpLocateReq request) {
        String ip = request.getIp();
        if (!StringUtils.hasText(ip)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "ip 不能为空");
        }
        return baseUrl + "/"
                + UriUtils.encodePathSegment(ip.trim(), StandardCharsets.UTF_8)
                + "/json/";
    }

    @Override
    protected Map<String, String> buildHeaders() {
        return Map.of(
                "Accept", "application/json",
                "User-Agent", "api-service/0.0.1"
        );
    }

    @Override
    protected HttpMethod resolveHttpMethod(IpLocateReq request) {
        return HttpMethod.GET;
    }

    @Override
    protected IpLocateVO transformResponse(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            if (root == null || !root.isObject()) {
                throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "上游返回格式不正确");
            }
            if (hasUpstreamError(root)) {
                String reason = text(root, "reason");
                throw new BusinessException(ErrorCode.UPSTREAM_ERROR,
                        "IP 归属地查询失败" + (reason == null ? "" : ": " + reason));
            }
            return new IpLocateVO(
                    text(root, "ip"),
                    text(root, "country_name"),
                    text(root, "region"),
                    text(root, "city"),
                    text(root, "org"),
                    number(root, "latitude"),
                    number(root, "longitude"),
                    text(root, "timezone")
            );
        } catch (BusinessException ex) {
            throw ex;
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "上游返回格式无法解析", ex);
        }
    }

    private boolean hasUpstreamError(JsonNode root) {
        JsonNode error = root.get("error");
        if (error == null || error.isNull()) {
            return false;
        }
        if (error.isBoolean()) {
            return error.asBoolean();
        }
        return error.isString() && StringUtils.hasText(error.asString());
    }

    private String text(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node == null || node.isNull() ? null : node.asString();
    }

    private Double number(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node != null && node.isNumber() ? node.asDouble() : null;
    }

    private static String stripTrailingSlash(String url) {
        String normalized = url == null ? "" : url.trim();
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
