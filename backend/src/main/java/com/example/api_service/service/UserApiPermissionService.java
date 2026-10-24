package com.example.api_service.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.example.api_service.common.PageResult;
import com.example.api_service.model.dto.permission.PermissionApplyRequest;
import com.example.api_service.model.dto.permission.PermissionAuditRequest;
import com.example.api_service.model.entity.UserApiPermission;
import com.example.api_service.model.vo.permission.AdminPermissionVO;
import com.example.api_service.model.vo.permission.EndpointMarketVO;
import com.example.api_service.model.vo.permission.UserPermissionVO;

import java.util.List;

public interface UserApiPermissionService extends IService<UserApiPermission> {

    /**
     * 接口市场：所有接口目录 + 当前用户的申请状态。
     */
    List<EndpointMarketVO> market(Long userId);

    /**
     * 提交或重新提交权限申请。
     *
     * <p>待审批或已通过的接口不允许重复申请；已驳回或已收回的记录在原记录上重新提交。</p>
     */
    UserPermissionVO apply(Long userId, PermissionApplyRequest request);

    /**
     * 管理员分页查询申请工单。
     */
    PageResult<AdminPermissionVO> adminList(long page, long size, Integer status, String keyword);

    /**
     * 管理员审批申请：通过、驳回或收回权限。
     */
    UserPermissionVO audit(Long operatorId, Long permissionId, PermissionAuditRequest request);

    /**
     * 查询用户对指定接口已通过审批的权限记录。
     *
     * @return 已通过的权限记录；无权限时返回 null
     */
    UserApiPermission getApprovedPermission(Long userId, Long endpointId);
}
