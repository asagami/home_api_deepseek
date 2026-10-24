package com.example.api_service.security;

import com.example.api_service.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 管理员权限拦截器，在 JWT 鉴权之后执行。
 *
 * <p>仅对标注了 {@link RequireAdmin} 的 Controller / 方法生效，
 * 非管理员的请求直接返回 403，不进入业务逻辑。</p>
 */
@Component
@RequiredArgsConstructor
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final String ROLE_ADMIN = "ADMIN";

    private final AuthResponseWriter responseWriter;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        if (!(handler instanceof HandlerMethod handlerMethod) || !requiresAdmin(handlerMethod)) {
            return true;
        }

        UserPrincipal principal = UserContext.get();
        if (principal == null) {
            responseWriter.write(response, ErrorCode.UNAUTHORIZED, "未认证或登录已过期");
            return false;
        }
        if (!ROLE_ADMIN.equals(principal.role())) {
            responseWriter.write(response, ErrorCode.FORBIDDEN, "该接口仅限管理员访问");
            return false;
        }
        return true;
    }

    private boolean requiresAdmin(HandlerMethod handlerMethod) {
        return handlerMethod.hasMethodAnnotation(RequireAdmin.class)
                || handlerMethod.getBeanType().isAnnotationPresent(RequireAdmin.class);
    }
}
