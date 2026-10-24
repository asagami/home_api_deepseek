package com.example.api_service.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.example.api_service.model.entity.RechargeOrder;

import java.time.LocalDateTime;

public interface RechargeOrderService extends IService<RechargeOrder> {

    /**
     * 乐观锁标记订单已支付。
     *
     * @return true 本次请求完成了状态流转；false 订单已被支付或关闭
     */
    boolean markPaid(String orderNo, String tradeNo, LocalDateTime payTime);

    /**
     * 关闭超时订单，仅对当前用户仍处于待支付的订单生效。
     *
     * @return true 本次请求完成了关闭；false 订单已被支付或关闭
     */
    boolean markClosed(String orderNo, Long userId);
}
