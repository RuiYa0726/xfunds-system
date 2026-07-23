package com.xfunds.service;

import com.xfunds.dto.LoginRequest;
import com.xfunds.dto.LoginResponse;

/**
 * 认证服务接口
 */
public interface AuthService {

    /**
     * 用户登录
     */
    LoginResponse login(LoginRequest request);

    /**
     * 获取当前登录用户信息
     */
    LoginResponse getUserInfo();

    /**
     * 登出
     */
    void logout();
}
