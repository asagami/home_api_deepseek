package com.example.api_service.engine.handlers.log;

import com.example.api_service.model.dto.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 当前用户调用日志分页查询参数。
 */
@Schema(name = "LogQueryPageReq", description = "个人调用日志分页查询参数")
public class LogQueryPageReq extends BasePageRequest {

    @Schema(description = "按 HTTP 状态码精确筛选，null 表示不筛选", example = "200")
    @Min(value = 100, message = "httpStatus 不能小于 100")
    @Max(value = 599, message = "httpStatus 不能大于 599")
    private Integer httpStatus;

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(Integer httpStatus) {
        this.httpStatus = httpStatus;
    }
}
