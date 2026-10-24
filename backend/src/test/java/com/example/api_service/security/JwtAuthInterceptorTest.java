package com.example.api_service.security;

import com.example.api_service.model.entity.SysUser;
import com.example.api_service.service.SysUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthInterceptorTest {

    private static final String SECRET = "test-jwt-secret-at-least-32-bytes-long-for-hs256";
    private static final Long USER_ID = 7L;

    @Mock
    private SysUserService sysUserService;

    private JwtUtils jwtUtils;
    private JwtAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils(SECRET, 3_600_000L);
        AuthResponseWriter responseWriter = new AuthResponseWriter(JsonMapper.builder().build());
        interceptor = new JwtAuthInterceptor(jwtUtils, sysUserService, responseWriter);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldRejectMissingBearerToken() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request(null), response, new Object());

        assertFalse(allowed);
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":40105"));
    }

    @Test
    void shouldRejectInvalidToken() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request("Bearer invalid.token.value"), response, new Object());

        assertFalse(allowed);
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":40106"));
    }

    @Test
    void shouldRejectBannedUser() throws Exception {
        String token = jwtUtils.generateToken(USER_ID, "bob", "USER");
        SysUser user = user();
        user.setStatus(0);
        when(sysUserService.getById(USER_ID)).thenReturn(user);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request("Bearer " + token), response, new Object());

        assertFalse(allowed);
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":40301"));
    }

    @Test
    void shouldAllowValidTokenAndPopulateContext() throws Exception {
        String token = jwtUtils.generateToken(USER_ID, "bob", "ADMIN");
        when(sysUserService.getById(USER_ID)).thenReturn(user());

        boolean allowed = interceptor.preHandle(request("Bearer " + token),
                new MockHttpServletResponse(), new Object());

        assertTrue(allowed);
        assertEquals(USER_ID, UserContext.getUserId());
        assertEquals("bob", UserContext.getRequired().username());
        assertEquals("ADMIN", UserContext.getRequired().role());

        interceptor.afterCompletion(request(null), new MockHttpServletResponse(), new Object(), null);
        assertNull(UserContext.get());
    }

    private MockHttpServletRequest request(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/console/users");
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        return request;
    }

    private SysUser user() {
        SysUser user = new SysUser();
        user.setId(USER_ID);
        user.setUsername("bob");
        user.setRole("ADMIN");
        user.setBalanceQuota(1000L);
        user.setStatus(1);
        return user;
    }
}
