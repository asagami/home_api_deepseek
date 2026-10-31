package com.example.api_service.service;

import com.example.api_service.common.PageResult;
import com.example.api_service.model.dto.recharge.RechargeOrderCreateRequest;
import com.example.api_service.model.dto.recharge.RechargeOrderPageRequest;
import com.example.api_service.model.vo.admin.AdminRechargeOrderVO;
import com.example.api_service.model.vo.recharge.RechargeOrderCreateVO;
import com.example.api_service.model.vo.recharge.RechargeOrderStatusVO;
import com.example.api_service.model.vo.recharge.RechargeOrderVO;
import com.example.api_service.model.vo.recharge.RechargePackageVO;

import java.util.List;

public interface RechargeService {

    /**
     * 返回全部充值套餐档位，按档位从低到高排列。
     */
    List<RechargePackageVO> listPackages();

    /**
     * 创建待支付订单并生成支付二维码内容串，二维码有效期 15 分钟。
     */
    RechargeOrderCreateVO createOrder(Long userId, RechargeOrderCreateRequest request);

    /**
     * 查询订单状态；待支付订单超过 expireTime 时自动流转为已关闭。
     */
    RechargeOrderStatusVO queryStatus(Long userId, String orderNo);

    /**
     * 分页查询当前用户的充值历史。
     */
    PageResult<RechargeOrderVO> pageOrders(Long userId, RechargeOrderPageRequest request);

    /**
     * 开发环境沙箱支付：以 MOCK_ 流水号完成支付并原子入账。
     */
    RechargeOrderStatusVO mockPay(Long userId, String orderNo);

    /**
     * 完成支付：乐观锁更新订单状态，并在同一事务内为用户原子加点。
     *
     * @param userId  订单所属用户
     * @param orderNo 平台订单号
     * @param tradeNo 第三方或沙箱支付流水号
     */
    RechargeOrderStatusVO completePayment(Long userId, String orderNo, String tradeNo);

    /**
     * 管理员直接充值：在同一事务内预占月度限额、写一条 source=ADMIN_GRANT 的已到账订单并加点。
     *
     * @param operatorId 操作管理员 ID
     * @param userId     被充值用户 ID
     * @param points     本次充值点数
     * @param remark     备注/原因，可为空
     * @return 新生成的充值订单（来源为管理员发放）
     */
    RechargeOrderVO grantByAdmin(Long operatorId, Long userId, long points, String remark);

    /**
     * 管理员分页查看指定用户的充值/发放记录。
     */
    PageResult<AdminRechargeOrderVO> pageOrdersForAdmin(Long userId, long page, long size,
                                                        Integer source, Integer status);
}
