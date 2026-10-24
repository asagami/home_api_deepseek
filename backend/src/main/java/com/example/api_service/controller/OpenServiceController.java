package com.example.api_service.controller;

import com.example.api_service.common.Result;
import com.example.api_service.model.dto.open.TextStatsRequest;
import com.example.api_service.model.vo.open.EchoVO;
import com.example.api_service.model.vo.open.TextStatsVO;
import com.example.api_service.security.ApiKeyContext;
import com.example.api_service.security.ApiKeyPrincipal;
import com.example.api_service.service.OpenService;
import com.example.api_service.service.QuotaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/services")
@RequiredArgsConstructor
@Tag(name = "Open API - Services", description = "使用 X-API-Key 鉴权的对外示例接口")
public class OpenServiceController {

    private static final int HTTP_OK = 200;

    private final OpenService openService;
    private final QuotaService quotaService;

    @GetMapping("/echo")
    @Operation(summary = "回声测试", description = "原样返回 message，用于验证鉴权与计费链路")
    public Result<EchoVO> echo(
            @RequestParam(defaultValue = "Hello, API!")
            @Size(max = 500, message = "message 长度不能超过 500") String message,
            HttpServletRequest request) {
        long startNanos = System.nanoTime();
        EchoVO result = openService.echo(message);
        consumeQuota(request, startNanos);
        return Result.success(result);
    }

    @PostMapping("/text-stats")
    @Operation(summary = "文本统计", description = "统计字符数、单词数、去重单词数及 Top 10 词频")
    public Result<TextStatsVO> textStats(@Valid @RequestBody TextStatsRequest body,
                                         HttpServletRequest request) {
        long startNanos = System.nanoTime();
        TextStatsVO result = openService.textStats(body.text());
        consumeQuota(request, startNanos);
        return Result.success(result);
    }

    private void consumeQuota(HttpServletRequest request, long startNanos) {
        ApiKeyPrincipal principal = ApiKeyContext.getRequired();
        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
        quotaService.consume(
                principal.userId(),
                principal.apiKeyId(),
                request.getRequestURI(),
                HTTP_OK,
                principal.costPoints(),
                latencyMs,
                resolveClientIp(request)
        );
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
