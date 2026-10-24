package com.example.api_service.controller;

import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;
import com.example.api_service.common.PageResult;
import com.example.api_service.common.Result;
import com.example.api_service.model.dto.recharge.RechargeOrderCreateRequest;
import com.example.api_service.model.dto.recharge.RechargeOrderPageRequest;
import com.example.api_service.model.vo.recharge.RechargeOrderCreateVO;
import com.example.api_service.model.vo.recharge.RechargeOrderStatusVO;
import com.example.api_service.model.vo.recharge.RechargeOrderVO;
import com.example.api_service.model.vo.recharge.RechargePackageVO;
import com.example.api_service.security.UserContext;
import com.example.api_service.service.RechargeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/console/recharge")
@Tag(name = "Console - Recharge", description = "开发者点数充值与扫码支付")
public class RechargeController {

    private final RechargeService rechargeService;
    /** 沙箱模拟支付开关，生产环境应通过配置关闭。 */
    private final boolean mockPayEnabled;

    public RechargeController(
            RechargeService rechargeService,
            @Value("${app.recharge.mock-pay-enabled:true}") boolean mockPayEnabled) {
        this.rechargeService = rechargeService;
        this.mockPayEnabled = mockPayEnabled;
    }

    @GetMapping("/packages")
    @Operation(summary = "获取充值套餐档位", description = "返回 5 档固定套餐，金额单位为分")
    public Result<List<RechargePackageVO>> packages() {
        return Result.success(rechargeService.listPackages());
    }

    @PostMapping("/create-order")
    @Operation(summary = "创建充值订单",
            description = "校验套餐与支付渠道，生成 15 分钟有效的待支付订单与二维码内容串")
    public Result<RechargeOrderCreateVO> createOrder(@Valid @RequestBody RechargeOrderCreateRequest request) {
        return Result.success(rechargeService.createOrder(UserContext.getUserId(), request));
    }

    @GetMapping("/status/{orderNo}")
    @Operation(summary = "查询订单状态",
            description = "待支付订单超过 expireTime 时自动流转为已关闭，并返回当前用户最新余额")
    public Result<RechargeOrderStatusVO> status(
            @PathVariable
            @NotBlank(message = "订单号不能为空")
            @Size(max = 64, message = "订单号长度不能超过 64")
            @Parameter(description = "平台订单号") String orderNo) {
        return Result.success(rechargeService.queryStatus(UserContext.getUserId(), orderNo));
    }

    @PostMapping("/orders")
    @Operation(summary = "分页查询充值记录", description = "仅返回当前用户的订单，按创建时间倒序")
    public Result<PageResult<RechargeOrderVO>> orders(
            @Valid @RequestBody RechargeOrderPageRequest request) {
        return Result.success(rechargeService.pageOrders(UserContext.getUserId(), request));
    }

    @PostMapping("/mock-pay/{orderNo}")
    @Operation(summary = "沙箱模拟支付（仅开发环境）",
            description = "以 MOCK_ 流水号完成支付，乐观锁更新订单并原子增加用户点数")
    public Result<RechargeOrderStatusVO> mockPay(
            @PathVariable
            @NotBlank(message = "订单号不能为空")
            @Size(max = 64, message = "订单号长度不能超过 64")
            @Parameter(description = "平台订单号") String orderNo) {
        if (!mockPayEnabled) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "沙箱模拟支付接口已禁用");
        }
        log.info("沙箱模拟支付: userId={}, orderNo={}", UserContext.getUserId(), orderNo);
        return Result.success(rechargeService.mockPay(UserContext.getUserId(), orderNo));
    }
}
