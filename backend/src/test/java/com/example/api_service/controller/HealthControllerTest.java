package com.example.api_service.controller;

import com.example.api_service.common.Result;
import com.example.api_service.model.vo.HealthVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("健康检查控制器单元测试")
class HealthControllerTest {

    @Test
    @DisplayName("应返回 UP 状态及配置的服务名与版本")
    void shouldReportUpWithConfiguredNameAndVersion() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.application.name", "api-service-custom")
                .withProperty("app.version", "1.2.3");
        HealthController controller = new HealthController(environment);

        long before = System.currentTimeMillis();
        Result<HealthVO> result = controller.health();
        long after = System.currentTimeMillis();

        assertEquals(0, result.code());
        HealthVO health = result.data();
        assertEquals("UP", health.status());
        assertEquals("api-service-custom", health.service());
        assertEquals("1.2.3", health.version());
        assertTrue(health.timestamp() >= before && health.timestamp() <= after,
                "时间戳应落在本次调用区间内");
    }

    @Test
    @DisplayName("缺少配置时应回落到默认服务名与版本")
    void shouldFallBackToDefaultsWhenPropertiesMissing() {
        HealthController controller = new HealthController(new MockEnvironment());

        HealthVO health = controller.health().data();

        assertEquals("UP", health.status());
        assertEquals("api-service", health.service());
        assertEquals("0.0.1-SNAPSHOT", health.version());
    }
}
