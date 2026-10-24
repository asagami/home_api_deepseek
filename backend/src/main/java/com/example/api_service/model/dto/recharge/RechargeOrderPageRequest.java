package com.example.api_service.model.dto.recharge;

import com.example.api_service.model.dto.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(name = "RechargeOrderPageRequest", description = "当前用户充值订单分页查询参数")
public class RechargeOrderPageRequest extends BasePageRequest {

    @Schema(description = "订单状态筛选：0-待支付，1-已支付，2-已关闭；不传表示全部", nullable = true)
    @Min(value = 0, message = "status 只能是 0、1 或 2")
    @Max(value = 2, message = "status 只能是 0、1 或 2")
    private Integer status;

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
