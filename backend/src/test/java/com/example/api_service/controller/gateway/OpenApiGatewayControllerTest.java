package com.example.api_service.controller.gateway;

import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;
import com.example.api_service.common.Result;
import com.example.api_service.engine.AbstractApiHandler;
import com.example.api_service.engine.AbstractSqlApiHandler;
import com.example.api_service.engine.context.ApiContext;
import com.example.api_service.model.dto.BasePageRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiGatewayControllerTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @AfterEach
    void clearContext() {
        ApiContext.clear();
    }

    @Test
    void shouldRouteDeserializeValidateAndExecute() {
        OpenApiGatewayController controller = controller(new EchoHandler());
        ApiContext context = bindContext(1, 1.0d);

        Result<?> result = controller.dispatch("{\"message\":\"hello\"}",
                request("/api/v1/test/echo"));

        assertEquals(ErrorCode.SUCCESS.getCode(), result.code());
        assertEquals(new EchoResponse("hello"), result.data());
        assertTrue(context.isBillable());
    }

    @Test
    void shouldReturnNotFoundForUnregisteredPath() {
        OpenApiGatewayController controller = controller(new EchoHandler());
        bindContext(1, 1.0d);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> controller.dispatch("{}", request("/api/v1/test/missing")));

        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void shouldRejectMalformedJsonBody() {
        OpenApiGatewayController controller = controller(new EchoHandler());
        bindContext(1, 1.0d);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> controller.dispatch("{\"message\":", request("/api/v1/test/echo")));

        assertEquals(ErrorCode.PARAM_ERROR, exception.getErrorCode());
    }

    @Test
    void shouldRejectWhenBeanValidationFails() {
        OpenApiGatewayController controller = controller(new EchoHandler());
        bindContext(1, 1.0d);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> controller.dispatch("{\"message\":\"\"}", request("/api/v1/test/echo")));

        assertEquals(ErrorCode.PARAM_ERROR, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("message"));
    }

    @Test
    void shouldResolveRequestTypeThroughIndirectGenericBaseClass() {
        OpenApiGatewayController controller = controller(new EchoSqlHandler());
        bindContext(1, 1.0d);

        Result<?> result = controller.dispatch("{\"message\":\"sql\"}",
                request("/api/v1/test/echo-sql"));

        assertEquals(new EchoResponse("sql"), result.data());
    }

    @Test
    void shouldApplyPaginationBillingMultiplier() {
        OpenApiGatewayController controller = controller(new PagedHandler());
        bindContext(2, 0.5d);

        controller.dispatch("{\"pageSize\":50}", request("/api/v1/test/paged"));

        // ceil(2 * 0.5 * ceil(50 / 20)) = 3
        assertEquals(3, ApiContext.calculateFinalCost());
    }

    private OpenApiGatewayController controller(AbstractApiHandler<?, ?> handler) {
        OpenApiGatewayController controller = new OpenApiGatewayController(
                List.of(handler), objectMapper, validator);
        controller.initHandlerRoutes();
        return controller;
    }

    private ApiContext bindContext(int baseCostPerCall, double customDiscount) {
        ApiContext context = new ApiContext();
        context.setUserId(1L);
        context.setApiKeyId(2L);
        context.setEndpointId(3L);
        context.setBaseCostPerCall(baseCostPerCall);
        context.setCustomDiscount(customDiscount);
        context.setUserBalanceQuota(1000L);
        ApiContext.set(context);
        return context;
    }

    private MockHttpServletRequest request(String path) {
        return new MockHttpServletRequest("POST", path);
    }

    private record EchoRequest(@NotBlank(message = "message 不能为空") String message) {
    }

    private record EchoResponse(String message) {
    }

    private static final class EchoHandler extends AbstractApiHandler<EchoRequest, EchoResponse> {

        @Override
        public String getEndpointPath() {
            return "/api/v1/test/echo";
        }

        @Override
        protected EchoResponse doExecute(EchoRequest request) {
            return new EchoResponse(request.message());
        }
    }

    private static final class EchoSqlHandler
            extends AbstractSqlApiHandler<EchoRequest, EchoResponse> {

        @Override
        public String getEndpointPath() {
            return "/api/v1/test/echo-sql";
        }

        @Override
        protected EchoResponse doExecute(EchoRequest request) {
            return new EchoResponse(request.message());
        }
    }

    private static final class PagedHandler extends AbstractApiHandler<PagedRequest, String> {

        @Override
        public String getEndpointPath() {
            return "/api/v1/test/paged";
        }

        @Override
        protected String doExecute(PagedRequest request) {
            return "ok";
        }
    }

    /** 仅用于验证 BasePageRequest 的计费倍率能被模板读取。 */
    public static class PagedRequest extends BasePageRequest {
    }
}
