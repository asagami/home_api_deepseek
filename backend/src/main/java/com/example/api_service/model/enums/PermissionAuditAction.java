package com.example.api_service.model.enums;

/**
 * 管理员审批动作。
 */
public enum PermissionAuditAction {

    /** 通过申请并授予调用权限 */
    APPROVE,

    /** 驳回申请 */
    REJECT,

    /** 收回已授予的权限 */
    REVOKE
}
