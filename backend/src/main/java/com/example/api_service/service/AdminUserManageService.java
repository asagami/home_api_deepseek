package com.example.api_service.service;

import com.example.api_service.common.PageResult;
import com.example.api_service.model.vo.admin.AdminRechargeOrderVO;
import com.example.api_service.model.vo.admin.AdminRechargeQuotaVO;
import com.example.api_service.model.vo.admin.AdminUserVO;

public interface AdminUserManageService {

    /**
     * 分页查询用户，支持用户名/邮箱模糊搜索与状态筛选。
     */
    PageResult<AdminUserVO> list(long page, long size, String keyword, Integer status);

    /**
     * 启用或封禁用户。管理员不能封禁自己。
     */
    void updateStatus(Long operatorId, Long userId, Integer status);

    /**
     * 管理员为用户直接充值：写充值流水、扣减月度限额并加点，返回更新后的用户信息。
     */
    AdminUserVO recharge(Long operatorId, Long userId, long amount, String remark);

    /**
     * 查询当前管理员本月对指定账号的充值额度使用情况。
     */
    AdminRechargeQuotaVO quotaUsage(Long operatorId, Long userId);

    /**
     * 分页查询指定用户的充值/发放记录（管理员视角）。
     */
    PageResult<AdminRechargeOrderVO> rechargeOrders(Long userId, long page, long size,
                                                    Integer source, Integer status);
}
