package com.example.api_service.engine.external;

import com.example.api_service.model.dto.admin.codegen.ApiCodeGenSpec;
import com.example.api_service.model.vo.admin.codegen.CodeGenPreviewVO;
import com.example.api_service.service.impl.ApiCodeGenServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ApiCodeGenService 单元测试")
class ApiCodeGenServiceTest {

    private final ApiCodeGenServiceImpl codeGenService = new ApiCodeGenServiceImpl(null, null);

    @Test
    @DisplayName("preview 应该能正确生成 DTO/VO/Handler 与 SQL")
    void shouldRenderCodeCorrectly() {
        ApiCodeGenSpec spec = ApiCodeGenSpec.builder()
                .domain("order")
                .apiName("海外订单分页查询")
                .description("按状态分页检索海外PG订单")
                .endpointPath("/api/v1/pg/orders")
                .costPerCall(2)
                .engineType("EXTERNAL_SQL")
                .targetDataSourceKey("pg-local")
                .paged(true)
                .requestFields(List.of(
                        ApiCodeGenSpec.FieldSpec.builder().name("status").type("String").description("状态").build()
                ))
                .responseFields(List.of(
                        ApiCodeGenSpec.FieldSpec.builder().name("orderId").type("String").description("订单ID").build(),
                        ApiCodeGenSpec.FieldSpec.builder().name("amount").type("BigDecimal").description("金额").build()
                ))
                .build();

        CodeGenPreviewVO previewVO = codeGenService.preview(spec);

        assertNotNull(previewVO);
        assertEquals("PgOrdersPageReq", previewVO.getReqClassName());
        assertEquals("PgOrdersOpenVO", previewVO.getVoClassName());
        assertEquals("PgOrdersApiHandler", previewVO.getHandlerClassName());

        assertTrue(previewVO.getReqCode().contains("public class PgOrdersPageReq extends BasePageRequest"));
        assertTrue(previewVO.getReqCode().contains("private String status;"));

        assertTrue(previewVO.getVoCode().contains("public record PgOrdersOpenVO("));
        assertTrue(previewVO.getVoCode().contains("String orderId"));
        assertTrue(previewVO.getVoCode().contains("BigDecimal amount"));

        assertTrue(previewVO.getHandlerCode().contains("extends AbstractExternalSqlApiHandler<PgOrdersPageReq, PageResult<PgOrdersOpenVO>>"));
        assertTrue(previewVO.getHandlerCode().contains("return \"/api/v1/pg/orders\";"));
        assertTrue(previewVO.getHandlerCode().contains("return \"pg-local\";"));

        assertTrue(previewVO.getMetaSql().contains("INSERT IGNORE INTO `api_endpoint_meta`"));
        assertTrue(previewVO.getMetaSql().contains("'/api/v1/pg/orders'"));
    }
}
