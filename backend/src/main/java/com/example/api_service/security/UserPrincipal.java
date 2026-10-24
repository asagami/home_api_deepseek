package com.example.api_service.security;

/**
 * 控制台登录用户身份。
 */
public record UserPrincipal(Long userId, String username, String role) {
}
