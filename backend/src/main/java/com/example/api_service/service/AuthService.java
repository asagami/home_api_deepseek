package com.example.api_service.service;

import com.example.api_service.model.dto.auth.ChangePasswordRequest;
import com.example.api_service.model.dto.auth.LoginRequest;
import com.example.api_service.model.dto.auth.RegisterRequest;
import com.example.api_service.model.vo.auth.LoginVO;
import com.example.api_service.model.vo.auth.UserProfileVO;

public interface AuthService {

    /**
     * 注册开发者账号，初始额度 1000 次。
     */
    UserProfileVO register(RegisterRequest request);

    /**
     * 登录并签发 JWT。
     */
    LoginVO login(LoginRequest request);

    /**
     * 查询当前用户信息与剩余额度。
     */
    UserProfileVO getProfile(Long userId);

    /**
     * 修改密码：校验旧密码后更新哈希并递增会话版本，
     * 返回为当前设备换发的新 JWT（其他设备的历史令牌全部失效）。
     *
     * @param userId  当前登录用户，由 JWT 上下文提供，禁止使用请求体传入的 ID
     * @param request 旧密码与新密码
     */
    LoginVO changePassword(Long userId, ChangePasswordRequest request);
}
