package com.example.api_service.service;

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
}
