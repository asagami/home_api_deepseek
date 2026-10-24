package com.example.api_service.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.example.api_service.mapper.RechargeOrderMapper;
import com.example.api_service.model.entity.RechargeOrder;
import com.example.api_service.service.RechargeOrderService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class RechargeOrderServiceImpl extends ServiceImpl<RechargeOrderMapper, RechargeOrder>
        implements RechargeOrderService {

    @Override
    public boolean markPaid(String orderNo, String tradeNo, LocalDateTime payTime) {
        return baseMapper.markPaid(orderNo, tradeNo, payTime) > 0;
    }

    @Override
    public boolean markClosed(String orderNo, Long userId) {
        return baseMapper.markClosed(orderNo, userId) > 0;
    }
}
