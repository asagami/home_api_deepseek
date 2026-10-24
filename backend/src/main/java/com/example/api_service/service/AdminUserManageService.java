package com.example.api_service.service;

import com.example.api_service.common.PageResult;
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
     * 为用户追加额度，返回更新后的用户信息。
     */
    AdminUserVO recharge(Long userId, long amount);
}
