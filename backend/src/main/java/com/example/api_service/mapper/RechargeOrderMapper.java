package com.example.api_service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.api_service.model.entity.RechargeOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface RechargeOrderMapper extends BaseMapper<RechargeOrder> {

    /**
     * 乐观锁标记支付成功：仅当订单仍为待支付时更新，保证同一订单只会入账一次。
     *
     * @return 受影响行数，0 表示订单已被其他请求支付或关闭
     */
    @Update("""
            UPDATE recharge_order
            SET status = 1,
                trade_no = #{tradeNo},
                pay_time = #{payTime},
                update_time = NOW()
            WHERE order_no = #{orderNo}
              AND status = 0
              AND is_deleted = 0
            """)
    int markPaid(@Param("orderNo") String orderNo,
                 @Param("tradeNo") String tradeNo,
                 @Param("payTime") LocalDateTime payTime);

    /**
     * 关闭超时订单：仅当订单仍为待支付时生效，避免覆盖已支付状态。
     *
     * @return 受影响行数
     */
    @Update("""
            UPDATE recharge_order
            SET status = 2,
                update_time = NOW()
            WHERE order_no = #{orderNo}
              AND user_id = #{userId}
              AND status = 0
              AND is_deleted = 0
            """)
    int markClosed(@Param("orderNo") String orderNo, @Param("userId") Long userId);
}
