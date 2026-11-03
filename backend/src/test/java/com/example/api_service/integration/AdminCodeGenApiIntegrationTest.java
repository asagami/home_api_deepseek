package com.example.api_service.integration;

import com.example.api_service.model.dto.admin.codegen.ApiCodeGenSpec;
import com.example.api_service.model.entity.SysUser;
import com.example.api_service.model.vo.admin.codegen.ApiEndpointSummaryVO;
import com.example.api_service.model.vo.admin.codegen.CodeGenPreviewVO;
import com.example.api_service.model.vo.admin.codegen.DataSourceOptionVO;
import com.example.api_service.service.ApiCodeGenService;
import com.example.api_service.service.EmailService;
import com.example.api_service.service.SysUserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 接口代码生成器控制器集成测试。
 *
 * <p>{@link ApiCodeGenService} 以 Mock 替换，聚焦验证控制器层的路由、管理员鉴权、
 * Bean Validation 与出参透传；代码渲染逻辑本身由 {@code ApiCodeGenServiceTest} 覆盖。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminCodeGenApiIntegrationTest {

    private static final String AUTH_HEADER = "Authorization";
    private static final String PASSWORD = "Str0ngPass2026";
    private static final String ENDPOINT_PATH = "/api/v1/pg/orders";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SysUserService sysUserService;

    @MockitoBean
    private ApiCodeGenService apiCodeGenService;

    /** 集成测试不依赖真实 Redis / SMTP，注册验证码校验由该 Mock 通过 */
    @MockitoBean
    private EmailService emailService;

    @Test
    void shouldRejectNonAdminOnCodeGenEndpoints() throws Exception {
        String token = registerAndLogin("codegen_plain_user");

        mockMvc.perform(get("/api/console/admin/codegen/datasources")
                        .header(AUTH_HEADER, bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));

        mockMvc.perform(post("/api/console/admin/codegen/preview")
                        .header(AUTH_HEADER, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validSpec())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));

        verify(apiCodeGenService, never()).preview(any());
        verify(apiCodeGenService, never()).getAvailableDataSources();
    }

    @Test
    void shouldPreviewGeneratedCodeForAdmin() throws Exception {
        String token = createAdminToken("codegen_admin_preview");
        when(apiCodeGenService.preview(any(ApiCodeGenSpec.class))).thenReturn(
                CodeGenPreviewVO.builder()
                        .reqClassName("OrdersQueryReq")
                        .reqCode("public class OrdersQueryReq {}")
                        .voClassName("OrdersVO")
                        .voCode("public class OrdersVO {}")
                        .handlerClassName("OrdersQueryHandler")
                        .handlerCode("public class OrdersQueryHandler {}")
                        .metaSql("INSERT INTO api_endpoint_meta ...")
                        .build());

        mockMvc.perform(post("/api/console/admin/codegen/preview")
                        .header(AUTH_HEADER, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validSpec())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.reqClassName").value("OrdersQueryReq"))
                .andExpect(jsonPath("$.data.voClassName").value("OrdersVO"))
                .andExpect(jsonPath("$.data.handlerClassName").value("OrdersQueryHandler"))
                .andExpect(jsonPath("$.data.metaSql").value("INSERT INTO api_endpoint_meta ..."));

        ArgumentCaptor<ApiCodeGenSpec> specCaptor = ArgumentCaptor.forClass(ApiCodeGenSpec.class);
        verify(apiCodeGenService).preview(specCaptor.capture());
        assertEquals(ENDPOINT_PATH, specCaptor.getValue().getEndpointPath());
        assertEquals("order", specCaptor.getValue().getDomain());
        assertEquals(2, specCaptor.getValue().getCostPerCall());
    }

    @Test
    void shouldRejectInvalidSpecBeforeInvokingService() throws Exception {
        String token = createAdminToken("codegen_admin_validation");

        Map<String, Object> invalidPath = validSpec();
        invalidPath.put("endpointPath", "//evil.example.com/steal");

        mockMvc.perform(post("/api/console/admin/codegen/preview")
                        .header(AUTH_HEADER, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(invalidPath)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));

        Map<String, Object> missingName = validSpec();
        missingName.remove("apiName");

        mockMvc.perform(post("/api/console/admin/codegen/preview")
                        .header(AUTH_HEADER, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(missingName)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));

        verify(apiCodeGenService, never()).preview(any());
    }

    @Test
    void shouldWriteAndUpdateWorkspaceForAdmin() throws Exception {
        String token = createAdminToken("codegen_admin_write");
        when(apiCodeGenService.writeToWorkspace(any(ApiCodeGenSpec.class)))
                .thenReturn("/workspace/handlers/order");
        when(apiCodeGenService.updateWorkspace(any(ApiCodeGenSpec.class)))
                .thenReturn("/workspace/handlers/order");

        mockMvc.perform(post("/api/console/admin/codegen/write-disk")
                        .header(AUTH_HEADER, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validSpec())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value("/workspace/handlers/order"));

        mockMvc.perform(post("/api/console/admin/codegen/update")
                        .header(AUTH_HEADER, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validSpec())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value("/workspace/handlers/order"));

        verify(apiCodeGenService).writeToWorkspace(any(ApiCodeGenSpec.class));
        verify(apiCodeGenService).updateWorkspace(any(ApiCodeGenSpec.class));
    }

    @Test
    void shouldListEndpointsDetailAndDataSources() throws Exception {
        String token = createAdminToken("codegen_admin_query");
        when(apiCodeGenService.listConfiguredEndpoints()).thenReturn(List.of(
                ApiEndpointSummaryVO.builder()
                        .path(ENDPOINT_PATH)
                        .name("订单分页查询")
                        .engineType("EXTERNAL_SQL")
                        .costPerCall(2)
                        .status(1)
                        .hasConfig(true)
                        .build()));
        when(apiCodeGenService.getEndpointSpec(ENDPOINT_PATH)).thenReturn(
                ApiCodeGenSpec.builder()
                        .domain("order")
                        .apiName("订单分页查询")
                        .endpointPath(ENDPOINT_PATH)
                        .costPerCall(2)
                        .engineType("EXTERNAL_SQL")
                        .build());
        when(apiCodeGenService.getAvailableDataSources()).thenReturn(List.of(
                DataSourceOptionVO.builder()
                        .key("pg-local")
                        .name("本地 PostgreSQL")
                        .dbType("POSTGRESQL")
                        .jdbcUrl("jdbc:postgresql://localhost:5432/postgres")
                        .build()));

        mockMvc.perform(get("/api/console/admin/codegen/endpoints")
                        .header(AUTH_HEADER, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].path").value(ENDPOINT_PATH))
                .andExpect(jsonPath("$.data[0].engineType").value("EXTERNAL_SQL"))
                .andExpect(jsonPath("$.data[0].hasConfig").value(true));

        mockMvc.perform(get("/api/console/admin/codegen/endpoint-detail")
                        .header(AUTH_HEADER, bearer(token))
                        .param("path", ENDPOINT_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.domain").value("order"))
                .andExpect(jsonPath("$.data.endpointPath").value(ENDPOINT_PATH));

        mockMvc.perform(get("/api/console/admin/codegen/datasources")
                        .header(AUTH_HEADER, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].key").value("pg-local"))
                .andExpect(jsonPath("$.data[0].dbType").value("POSTGRESQL"));
    }

    private Map<String, Object> validSpec() {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("domain", "order");
        spec.put("apiName", "订单分页查询");
        spec.put("description", "从外部 PG 库分页检索订单");
        spec.put("endpointPath", ENDPOINT_PATH);
        spec.put("costPerCall", 2);
        spec.put("engineType", "EXTERNAL_SQL");
        spec.put("targetDataSourceKey", "pg-local");
        spec.put("paged", true);
        spec.put("customSql", "SELECT id, amount FROM orders WHERE 1=1");
        return spec;
    }

    private String createAdminToken(String username) throws Exception {
        register(username);
        SysUser user = findUser(username);
        user.setRole("ADMIN");
        sysUserService.updateById(user);
        return login(username);
    }

    private String registerAndLogin(String username) throws Exception {
        register(username);
        return login(username);
    }

    private void register(String username) throws Exception {
        String body = json(Map.of(
                "username", username,
                "email", username + "@example.com",
                "code", "123456",
                "password", PASSWORD));
        mockMvc.perform(post("/api/console/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/console/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("accessToken").asString();
    }

    private SysUser findUser(String username) {
        return sysUserService.lambdaQuery().eq(SysUser::getUsername, username).one();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object value) {
        return objectMapper.writeValueAsString(value);
    }
}
