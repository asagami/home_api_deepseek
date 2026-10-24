package com.example.api_service.security;

import com.example.api_service.common.ErrorCode;
import com.example.api_service.common.Result;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 在拦截器链中直接输出统一的 {@link Result} 错误响应。
 * 拦截器先于 Controller 执行，直接写响应可保证鉴权失败时不会进入业务逻辑。
 */
@Component
@RequiredArgsConstructor
public class AuthResponseWriter {

    private final ObjectMapper objectMapper;

    public void write(HttpServletResponse response, ErrorCode errorCode, String message) throws IOException {
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(Result.failure(errorCode, message)));
    }
}
