package com.example.api_service.engine;

import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * 第三方 HTTP 转发类业务模板。
 *
 * <p>统一封装带超时控制的 Spring 6 {@link RestClient}，负责 POST/GET 转发、
 * 请求头注入与响应清洗。上游密钥必须由子类从配置读取后经 {@link #buildHeaders()}
 * 返回，严禁硬编码。</p>
 *
 * @param <Req>  请求 DTO
 * @param <Resp> 清洗后的响应 VO
 */
@Slf4j
public abstract class AbstractHttpProxyApiHandler<Req, Resp>
        extends AbstractApiHandler<Req, Resp> {

    /** 建立连接超时。 */
    protected static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    /** 等待上游响应超时。 */
    protected static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    protected final RestClient restClient;

    protected AbstractHttpProxyApiHandler() {
        this(createRestClient());
    }

    /**
     * 供测试或自定义连接池场景注入预配置的 RestClient。
     */
    protected AbstractHttpProxyApiHandler(RestClient restClient) {
        this.restClient = Objects.requireNonNull(restClient, "restClient 不能为空");
    }

    /**
     * 构建带 2s 连接超时与 5s 读取超时的默认 RestClient。
     */
    protected static RestClient createRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * 目标地址，由子类根据请求参数拼装。
     */
    protected abstract String buildTargetUrl(Req request);

    /**
     * 转发请求头，上游密钥等敏感信息应从配置读取。
     */
    protected abstract Map<String, String> buildHeaders();

    /**
     * 将上游原始 JSON 清洗为平台标准 VO，避免把第三方字段直接透出。
     */
    protected abstract Resp transformResponse(String rawJson);

    /**
     * 请求方法，默认 POST；只读查询可覆盖为 GET。
     */
    protected HttpMethod resolveHttpMethod(Req request) {
        return HttpMethod.POST;
    }

    /**
     * 请求体，默认直接序列化整个请求 DTO；GET 场景不会使用。
     */
    protected Object buildRequestBody(Req request) {
        return request;
    }

    @Override
    protected final Resp doExecute(Req request) {
        String targetUrl = buildTargetUrl(request);
        Map<String, String> headers = buildHeaders();

        try {
            String rawJson = exchange(request, targetUrl, headers);
            if (rawJson == null || rawJson.isBlank()) {
                throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "上游服务返回空响应");
            }
            return transformResponse(rawJson);
        } catch (RestClientResponseException ex) {
            log.error("上游返回异常状态: url={}, status={}",
                    targetUrl, ex.getStatusCode().value(), ex);
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR,
                    "上游服务返回异常状态: " + ex.getStatusCode().value(), ex);
        } catch (ResourceAccessException ex) {
            log.error("上游连接失败或超时: url={}", targetUrl, ex);
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR,
                    "连接上游服务超时或失败，请稍后重试", ex);
        } catch (RestClientException ex) {
            log.error("上游调用失败: url={}", targetUrl, ex);
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR,
                    "调用上游服务失败，请稍后重试", ex);
        }
    }

    private String exchange(Req request, String targetUrl, Map<String, String> headers) {
        HttpMethod method = resolveHttpMethod(request);
        RestClient.RequestBodySpec spec = restClient.method(method).uri(targetUrl);
        if (headers != null && !headers.isEmpty()) {
            spec = spec.headers(targetHeaders -> headers.forEach(targetHeaders::set));
        }

        if (HttpMethod.GET.equals(method) || HttpMethod.HEAD.equals(method)) {
            return spec.retrieve().body(String.class);
        }

        Object requestBody = buildRequestBody(request);
        if (requestBody == null) {
            return spec.retrieve().body(String.class);
        }
        return spec.body(requestBody).retrieve().body(String.class);
    }
}
