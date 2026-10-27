package com.example.api_service.model.vo.admin.codegen;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据源下拉选项出参。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "DataSourceOptionVO", description = "数据源选项")
public class DataSourceOptionVO {

    @Schema(description = "数据源 Key", example = "pg-local")
    private String key;

    @Schema(description = "数据源描述名称", example = "本地 PostgreSQL")
    private String name;

    @Schema(description = "数据库类型", example = "POSTGRESQL")
    private String dbType;

    @Schema(description = "脱敏后的连接串", example = "jdbc:postgresql://localhost:5432/postgres")
    private String jdbcUrl;
}
