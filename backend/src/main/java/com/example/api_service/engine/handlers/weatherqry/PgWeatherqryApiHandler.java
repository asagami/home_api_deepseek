package com.example.api_service.engine.handlers.weatherqry;

import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;
import com.example.api_service.engine.AbstractHttpProxyApiHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * 天气查询
 */
@Component
public class PgWeatherqryApiHandler extends AbstractHttpProxyApiHandler<PgWeatherqryPageReq, PgWeatherqryOpenVO> {

    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public PgWeatherqryApiHandler(
            ObjectMapper objectMapper,
            @Value("${app.proxy.weatherqry.base-url:https://api.example.com}") String baseUrl) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
    }

    @Override
    public String getEndpointPath() {
        return "/api/v1/pg/weatherqry";
    }

    @Override
    protected String buildTargetUrl(PgWeatherqryPageReq request) {
        return baseUrl;
    }

    @Override
    protected Map<String, String> buildHeaders() {
        return Map.of(
                "Accept", "application/json"
        );
    }

    @Override
    protected HttpMethod resolveHttpMethod(PgWeatherqryPageReq request) {
        return HttpMethod.GET;
    }

    @Override
    protected PgWeatherqryOpenVO transformResponse(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            if (root == null) {
                throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "上游返回空响应");
            }
            return new PgWeatherqryOpenVO(
                    root.hasNonNull("weather") ? root.get("weather").asText() : null,
                    root.hasNonNull("time") ? root.get("time").asText() : null,
                    root.hasNonNull("isxiayu") && root.get("isxiayu").asBoolean()
            );
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "解析上游响应失败: " + e.getMessage(), e);
        }
    }
}
