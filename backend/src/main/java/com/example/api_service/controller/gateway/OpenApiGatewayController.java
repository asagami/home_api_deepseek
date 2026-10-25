package com.example.api_service.controller.gateway;

import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;
import com.example.api_service.common.Result;
import com.example.api_service.engine.AbstractApiHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 开放服务统一动态网关。
 *
 * <p>所有 {@code /api/v1/**} 请求进入本控制器后，按请求路径路由到对应的
 * {@link AbstractApiHandler}。请求体统一为 JSON，先反序列化为 Handler 声明的
 * 泛型 Req 类型，再执行 Bean Validation，最后交给 Handler 模板执行。</p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Open API Gateway", description = "全 POST + JSON 的统一开放接口入口")
public class OpenApiGatewayController {

    private static final String OPEN_API_PREFIX = "/api/v1/";
    private static final String EMPTY_JSON_BODY = "{}";

    private final List<AbstractApiHandler<?, ?>> handlers;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    /** 路径 -> Handler 路由表，应用启动后只读。 */
    private final Map<String, AbstractApiHandler<?, ?>> handlerRoutes = new LinkedHashMap<>();

    /** 路径 -> Handler 的 Req 泛型 Class，避免每次请求重复反射。 */
    private final Map<String, Class<?>> requestTypeCache = new LinkedHashMap<>();

    /**
     * 收集 Spring 容器中的全部 Handler，注册路由并缓存 Req 类型。
     */
    @PostConstruct
    void initHandlerRoutes() {
        for (AbstractApiHandler<?, ?> handler : handlers) {
            String path = normalizePath(handler.getEndpointPath());
            if (!path.startsWith(OPEN_API_PREFIX)) {
                throw new IllegalStateException("Handler " + handler.getClass().getName()
                        + " 的接口路径必须以 " + OPEN_API_PREFIX + " 开头，实际为: " + path);
            }
            if (handlerRoutes.putIfAbsent(path, handler) != null) {
                throw new IllegalStateException("检测到重复的开放接口路径: " + path);
            }
            requestTypeCache.put(path, resolveRequestType(handler.getClass()));
            log.info("注册开放接口: path={}, handler={}", path, handler.getClass().getSimpleName());
        }
        log.info("开放接口动态网关初始化完成，共注册 {} 个 Handler", handlerRoutes.size());
    }

    /**
     * 唯一动态入口：所有 /api/v1/** 的 POST + JSON 请求都在这里分发。
     */
    @PostMapping("/api/v1/**")
    @Operation(summary = "动态开放接口网关",
            description = "按请求路径匹配 AbstractApiHandler，统一完成 JSON 反序列化与参数校验")
    public Result<?> dispatch(@RequestBody(required = false) String rawJsonBody,
                              HttpServletRequest request) {
        String path = normalizePath(request.getRequestURI());
        AbstractApiHandler<?, ?> handler = handlerRoutes.get(path);
        if (handler == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "接口不存在或未在服务目录中登记: " + path);
        }

        Class<?> requestType = requestTypeCache.get(path);
        Object requestBody = deserialize(rawJsonBody, requestType, path);
        if (requestBody == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请求体不能为空");
        }
        validateRequest(requestBody, path);
        return invoke(handler, requestBody);
    }

    private Object deserialize(String rawJsonBody, Class<?> requestType, String path) {
        String body = rawJsonBody == null || rawJsonBody.isBlank() ? EMPTY_JSON_BODY : rawJsonBody;
        try {
            return objectMapper.readValue(body, requestType);
        } catch (JacksonException ex) {
            // 仅记录错误类型与根因消息，不打印原始 body：
            // 1) body 可能含调用方的敏感业务数据；
            // 2) body 内嵌换行可伪造日志行（日志注入）。
            log.warn("开放接口请求体解析失败: path={}, errorType={}, message={}",
                    path, ex.getClass().getSimpleName(), rootMessage(ex));
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请求体不是合法的 JSON 或字段类型不匹配");
        }
    }

    /**
     * 提取 Jackson 异常的根因消息，去掉换行与控制字符以防日志注入，
     * 并截断长度避免单条日志过长。
     */
    private static String rootMessage(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage() == null ? "" : root.getMessage();
        String sanitized = message.replaceAll("[\\r\\n\\t]", " ").trim();
        return sanitized.length() > 200 ? sanitized.substring(0, 200) + "..." : sanitized;
    }

    private void validateRequest(Object requestBody, String path) {
        Set<ConstraintViolation<Object>> violations = validator.validate(requestBody);
        if (violations.isEmpty()) {
            return;
        }
        String message = violations.stream()
                .sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining("; "));
        log.warn("开放接口参数校验失败: path={}, message={}", path, message);
        throw new BusinessException(ErrorCode.PARAM_ERROR, message);
    }

    @SuppressWarnings("unchecked")
    private Result<?> invoke(AbstractApiHandler<?, ?> handler, Object requestBody) {
        AbstractApiHandler<Object, Object> typedHandler =
                (AbstractApiHandler<Object, Object>) handler;
        return typedHandler.execute(requestBody);
    }

    /**
     * 沿继承链解析 {@code AbstractApiHandler<Req, Resp>} 中 Req 的实际 Class。
     *
     * <p>支持间接继承（如 AbstractSqlApiHandler / AbstractEsApiHandler）以及
     * 中间泛型类的逐层类型变量替换。</p>
     */
    private Class<?> resolveRequestType(Class<?> handlerClass) {
        Map<TypeVariable<?>, Type> resolvedArguments = new LinkedHashMap<>();
        Class<?> current = handlerClass;

        while (current != null && current != Object.class) {
            Type genericSuperclass = current.getGenericSuperclass();
            if (genericSuperclass == null) {
                break;
            }
            if (!(genericSuperclass instanceof ParameterizedType parameterizedType)) {
                current = genericSuperclass instanceof Class<?> superClass ? superClass : null;
                continue;
            }

            Class<?> rawType = (Class<?>) parameterizedType.getRawType();
            TypeVariable<?>[] variables = rawType.getTypeParameters();
            Type[] arguments = parameterizedType.getActualTypeArguments();
            for (int i = 0; i < variables.length && i < arguments.length; i++) {
                resolvedArguments.put(variables[i], resolveType(arguments[i], resolvedArguments));
            }

            if (rawType == AbstractApiHandler.class) {
                Type requestType = resolvedArguments.get(rawType.getTypeParameters()[0]);
                return toClass(requestType, handlerClass);
            }
            current = rawType;
        }

        throw new IllegalStateException(
                "无法解析 Handler 的 Req 泛型类型: " + handlerClass.getName());
    }

    private Type resolveType(Type type, Map<TypeVariable<?>, Type> resolvedArguments) {
        if (type instanceof TypeVariable<?> variable) {
            return resolvedArguments.getOrDefault(variable, variable);
        }
        return type;
    }

    private Class<?> toClass(Type type, Class<?> handlerClass) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterizedType
                && parameterizedType.getRawType() instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof WildcardType wildcardType && wildcardType.getUpperBounds().length > 0) {
            return toClass(wildcardType.getUpperBounds()[0], handlerClass);
        }
        throw new IllegalStateException(
                "无法解析 Handler 的 Req 泛型类型: " + handlerClass.getName());
    }

    /** 去掉尾部斜杠，保证 /api/v1/foo/ 与 /api/v1/foo 命中同一个 Handler。 */
    private static String normalizePath(String path) {
        String normalized = path == null ? "" : path.trim();
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
