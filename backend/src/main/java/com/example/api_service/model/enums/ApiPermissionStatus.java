package com.example.api_service.model.enums;

/**
 * user_api_permission.status 的取值。
 */
public enum ApiPermissionStatus {

    PENDING(0),
    APPROVED(1),
    REJECTED(2),
    REVOKED(3);

    private final int code;

    ApiPermissionStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public boolean matches(Integer status) {
        return status != null && status == code;
    }

    public static boolean isApproved(Integer status) {
        return APPROVED.matches(status);
    }
}
