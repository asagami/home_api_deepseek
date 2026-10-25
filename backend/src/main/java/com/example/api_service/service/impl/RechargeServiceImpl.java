package com.example.api_service.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;
import com.example.api_service.common.PageResult;
import com.example.api_service.engine.billing.QuotaLedger;
import com.example.api_service.model.dto.recharge.RechargeOrderCreateRequest;
import com.example.api_service.model.dto.recharge.RechargeOrderPageRequest;
import com.example.api_service.model.entity.RechargeOrder;
import com.example.api_service.model.entity.SysUser;
import com.example.api_service.model.enums.PayChannelEnum;
import com.example.api_service.model.enums.RechargeOrderStatus;
import com.example.api_service.model.enums.RechargePackageEnum;
import com.example.api_service.model.vo.recharge.RechargeOrderCreateVO;
import com.example.api_service.model.vo.recharge.RechargeOrderStatusVO;
import com.example.api_service.model.vo.recharge.RechargeOrderVO;
import com.example.api_service.model.vo.recharge.RechargePackageVO;
import com.example.api_service.service.RechargeOrderService;
import com.example.api_service.service.RechargeService;
import com.example.api_service.service.SysUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
public class RechargeServiceImpl implements RechargeService {

    /** 默认二维码有效期，与需求约定的 15 分钟一致。 */
    private static final long DEFAULT_ORDER_TTL_MINUTES = 15;
    /** 订单号中的时间前缀，精确到秒。 */
    private static final DateTimeFormatter ORDER_NO_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    /** 沙箱支付流水号前缀。 */
    private static final String MOCK_TRADE_NO_PREFIX = "MOCK_";
    private static final SecureRandom ORDER_NO_RANDOM = new SecureRandom();

    private final RechargeOrderService rechargeOrderService;
    private final SysUserService sysUserService;
    private final QuotaLedger quotaLedger;
    private final String qrBaseUrl;
    private final long orderTtlMinutes;

    public RechargeServiceImpl(
            RechargeOrderService rechargeOrderService,
            SysUserService sysUserService,
            QuotaLedger quotaLedger,
            @Value("${app.recharge.qr-base-url:https://pay.example.com/qr}") String qrBaseUrl,
            @Value("${app.recharge.order-ttl-minutes:15}") long orderTtlMinutes) {
        this.rechargeOrderService = rechargeOrderService;
        this.sysUserService = sysUserService;
        this.quotaLedger = quotaLedger;
        this.qrBaseUrl = qrBaseUrl;
        this.orderTtlMinutes = orderTtlMinutes > 0 ? orderTtlMinutes : DEFAULT_ORDER_TTL_MINUTES;
    }

    @Override
    public List<RechargePackageVO> listPackages() {
        return Arrays.stream(RechargePackageEnum.values())
                .map(RechargePackageVO::from)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RechargeOrderCreateVO createOrder(Long userId, RechargeOrderCreateRequest request) {
        RechargePackageEnum pkg = RechargePackageEnum.fromPackageId(request.packageId());
        PayChannelEnum channel = PayChannelEnum.fromCode(request.payChannel());

        String orderNo = generateOrderNo();
        LocalDateTime now = LocalDateTime.now();

        RechargeOrder order = new RechargeOrder();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setPackageId(pkg.getPackageId());
        order.setAmountFen(pkg.getAmountFen());
        order.setTotalPoints(pkg.getTotalPoints());
        order.setPayChannel(channel.name());
        order.setStatus(RechargeOrderStatus.PENDING.getCode());
        order.setQrCodeUrl(buildQrCodeUrl(orderNo, channel));
        order.setExpireTime(now.plusMinutes(orderTtlMinutes));
        order.setIsDeleted(0);
        rechargeOrderService.save(order);

        log.info("充值订单创建成功: orderNo={}, userId={}, packageId={}, channel={}, totalPoints={}",
                orderNo, userId, pkg.getPackageId(), channel.name(), pkg.getTotalPoints());
        return RechargeOrderCreateVO.from(order, pkg);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RechargeOrderStatusVO queryStatus(Long userId, String orderNo) {
        RechargeOrder order = getOwnedOrder(userId, orderNo);
        if (RechargeOrderStatus.PENDING.matches(order.getStatus()) && isExpired(order)) {
            rechargeOrderService.markClosed(orderNo, userId);
            order = getOwnedOrder(userId, orderNo);
        }
        return RechargeOrderStatusVO.from(order, currentBalance(userId));
    }

    @Override
    public PageResult<RechargeOrderVO> pageOrders(Long userId, RechargeOrderPageRequest request) {
        Page<RechargeOrder> result = rechargeOrderService.lambdaQuery()
                .eq(RechargeOrder::getUserId, userId)
                .eq(request.getStatus() != null, RechargeOrder::getStatus, request.getStatus())
                .orderByDesc(RechargeOrder::getCreateTime)
                .orderByDesc(RechargeOrder::getId)
                .page(new Page<>(request.getSafePageNum(), request.getSafePageSize()));

        List<RechargeOrderVO> records = result.getRecords().stream()
                .map(RechargeOrderVO::from)
                .toList();
        return PageResult.of(records, result.getTotal(),
                (int) result.getCurrent(), (int) result.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RechargeOrderStatusVO mockPay(Long userId, String orderNo) {
        return completePayment(userId, orderNo, MOCK_TRADE_NO_PREFIX + System.currentTimeMillis());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RechargeOrderStatusVO completePayment(Long userId, String orderNo, String tradeNo) {
        RechargeOrder order = getOwnedOrder(userId, orderNo);
        if (RechargeOrderStatus.PAID.matches(order.getStatus())) {
            throw new BusinessException(ErrorCode.RECHARGE_ORDER_ALREADY_PAID);
        }
        if (RechargeOrderStatus.CLOSED.matches(order.getStatus())) {
            throw new BusinessException(ErrorCode.RECHARGE_ORDER_CLOSED);
        }
        if (isExpired(order)) {
            throw new BusinessException(ErrorCode.RECHARGE_ORDER_EXPIRED);
        }

        String normalizedTradeNo = (tradeNo == null || tradeNo.isBlank())
                ? MOCK_TRADE_NO_PREFIX + System.currentTimeMillis()
                : tradeNo.trim();
        LocalDateTime payTime = LocalDateTime.now();
        if (!rechargeOrderService.markPaid(orderNo, normalizedTradeNo, payTime)) {
            // 读取后到写入之间被其他请求抢先支付/关闭，保证同一订单只入账一次
            throw new BusinessException(ErrorCode.RECHARGE_ORDER_ALREADY_PAID);
        }

        long points = order.getTotalPoints() == null ? 0L : order.getTotalPoints();
        if (!quotaLedger.credit(userId, points, false)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        order.setStatus(RechargeOrderStatus.PAID.getCode());
        order.setTradeNo(normalizedTradeNo);
        order.setPayTime(payTime);
        long balance = currentBalance(userId);
        log.info("充值支付完成: orderNo={}, userId={}, tradeNo={}, points={}, balanceQuota={}",
                orderNo, userId, normalizedTradeNo, points, balance);
        return RechargeOrderStatusVO.from(order, balance);
    }

    private RechargeOrder getOwnedOrder(Long userId, String orderNo) {
        RechargeOrder order = rechargeOrderService.lambdaQuery()
                .eq(RechargeOrder::getOrderNo, orderNo)
                .eq(RechargeOrder::getUserId, userId)
                .one();
        if (order == null) {
            throw new BusinessException(ErrorCode.RECHARGE_ORDER_NOT_FOUND);
        }
        return order;
    }

    private boolean isExpired(RechargeOrder order) {
        return order.getExpireTime() == null || !order.getExpireTime().isAfter(LocalDateTime.now());
    }

    private long currentBalance(Long userId) {
        SysUser user = sysUserService.getById(userId);
        return user == null || user.getBalanceQuota() == null ? 0L : user.getBalanceQuota();
    }

    private String generateOrderNo() {
        int suffix = ORDER_NO_RANDOM.nextInt(1_000_000);
        return "R" + LocalDateTime.now().format(ORDER_NO_TIME_FORMAT) + String.format("%06d", suffix);
    }

    private String buildQrCodeUrl(String orderNo, PayChannelEnum channel) {
        String baseUrl = qrBaseUrl == null || qrBaseUrl.isBlank()
                ? "https://pay.example.com/qr" : qrBaseUrl.trim();
        String separator = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + separator + "orderNo=" + orderNo + "&channel=" + channel.name();
    }
}
