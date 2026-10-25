package com.example.api_service.security;

import com.example.api_service.common.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminAuthInterceptorTest {

    private AdminAuthInterceptor interceptor;

    /** 模拟未标注 @RequireAdmin 的新增接口——兜底逻辑正是为它存在。 */
    static class PlainController {
        public void handle() {
        }
    }

    static class AnnotatedController {
        @RequireAdmin
        public void handle() {
        }
    }

    @BeforeEach
    void setUp() {
        interceptor = new AdminAuthInterceptor(new AuthResponseWriter(JsonMapper.builder().build()));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldAllowNonAdminPathWithoutPrincipal() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/console/apikeys");

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertTrue(allowed);
    }

    @Test
    void shouldBlockAdminPathWithoutAnnotationEvenForNonHandlerMethod() throws Exception {
        // 路径兜底不依赖 HandlerMethod / 注解，任何 handler 形态都要过管理员校验
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/console/admin/users");

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertFalse(allowed);
        assertEquals(ErrorCode.UNAUTHORIZED.getHttpStatus().value(), response.getStatus());
    }

    @Test
    void shouldBlockNonAdminUserOnAdminPath() throws Exception {
        UserContext.set(new UserPrincipal(7L, "bob", "USER"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/console/admin/users");

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertFalse(allowed);
        assertEquals(ErrorCode.FORBIDDEN.getHttpStatus().value(), response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":40300"));
    }

    @Test
    void shouldAllowAdminOnAdminPathWithoutAnnotation() throws Exception {
        UserContext.set(new UserPrincipal(1L, "root", "ADMIN"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/console/admin/stats");

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertTrue(allowed);
    }

    @Test
    void shouldBlockNonAdminUserOnAnnotatedHandlerOutsideAdminPath() throws Exception {
        UserContext.set(new UserPrincipal(7L, "bob", "USER"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/console/other");
        HandlerMethod handler = new HandlerMethod(new AnnotatedController(), "handle");

        boolean allowed = interceptor.preHandle(request, response, handler);

        assertFalse(allowed);
        assertEquals(ErrorCode.FORBIDDEN.getHttpStatus().value(), response.getStatus());
    }

    @Test
    void shouldAllowAdminOnPlainHandlerOutsideAdminPath() throws Exception {
        UserContext.set(new UserPrincipal(1L, "root", "ADMIN"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/console/other");
        HandlerMethod handler = new HandlerMethod(new PlainController(), "handle");

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), handler);

        assertTrue(allowed);
    }
}
