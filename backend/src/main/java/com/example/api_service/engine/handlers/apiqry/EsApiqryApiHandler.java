package com.example.api_service.engine.handlers.apiqry;

import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;
import com.example.api_service.engine.AbstractHttpProxyApiHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Map;

/**
 * api用户分页查询
 */
@Component
public class EsApiqryApiHandler extends AbstractHttpProxyApiHandler<EsApiqryPageReq, EsApiqryOpenVO> {

    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public EsApiqryApiHandler(
            ObjectMapper objectMapper,
            @Value("${app.proxy.apiqry.base-url:https://api.example.com/v1}") String baseUrl) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
    }

    @Override
    public String getEndpointPath() {
        return "/api/v1/es/apiqry";
    }

    @Override
    protected String buildTargetUrl(EsApiqryPageReq request) {
        // TODO: 按需根据 request 拼装下游 API 目标路径与 Query 参数
        return baseUrl;
    }

    @Override
    protected Map<String, String> buildHeaders() {
        return Map.of(
                "Accept", "application/json"
        );
    }

    @Override
    protected HttpMethod resolveHttpMethod(EsApiqryPageReq request) {
        return HttpMethod.POST;
    }

    @Override
    protected EsApiqryOpenVO transformResponse(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            if (root == null) {
                throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "上游返回空响应");
            }
            return new EsApiqryOpenVO(
                        root.hasNonNull("userId") ? root.get("userId").asText() : null,
                        root.hasNonNull("username") ? root.get("username").asText() : null,
                        root.hasNonNull("superuser") && root.get("superuser").asBoolean(),
                        root.hasNonNull("validUntil") ? root.get("validUntil").asText() : null
            );
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "解析上游响应失败: " + e.getMessage(), e);
        }
    }
}
