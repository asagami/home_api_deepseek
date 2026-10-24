package com.example.api_service.controller;

import com.example.api_service.common.Result;
import com.example.api_service.model.vo.HealthVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查接口。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Health", description = "健康检查")
public class HealthController {

    private static final String STATUS_UP = "UP";

    private final Environment environment;

    @GetMapping("/health")
    @Operation(summary = "健康检查", description = "返回服务运行状态、名称与版本信息")
    public Result<HealthVO> health() {
        HealthVO health = new HealthVO(
                STATUS_UP,
                environment.getProperty("spring.application.name", "api-service"),
                environment.getProperty("app.version", "0.0.1-SNAPSHOT"),
                System.currentTimeMillis()
        );
        return Result.success(health);
    }
}
