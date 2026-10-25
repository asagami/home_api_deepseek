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
 * <p>两级判定：</p>
 * <ul>
 *   <li>路径兜底：凡是 {@code /api/console/admin/**} 下的请求一律强制校验
 *       {@code role == ADMIN}，不依赖注解——后续新增管理接口即使漏加
 *       {@link RequireAdmin} 也不会产生越权入口；</li>
 *   <li>注解兜底：管理前缀之外、但类或方法标注了 {@link RequireAdmin} 的接口，
 *       同样要求管理员角色。</li>
 * </ul>
 *
 * <p>非管理员请求直接返回 403，不进入业务逻辑。注意兜底基于 URI 前缀，
 * 新增管理控制器时必须落在 {@code /api/console/admin/} 路径下。</p>
 */
@Component
@RequiredArgsConstructor
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final String ROLE_ADMIN = "ADMIN";
    /** 管理端统一路径前缀，命中即强制管理员角色（注解兜底之外的第二道保险）。 */
    private static final String ADMIN_PATH_PREFIX = "/api/console/admin/";

    private final AuthResponseWriter responseWriter;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        boolean adminPath = request.getRequestURI().startsWith(ADMIN_PATH_PREFIX);
        boolean annotated = handler instanceof HandlerMethod handlerMethod && requiresAdmin(handlerMethod);
        if (!adminPath && !annotated) {
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
