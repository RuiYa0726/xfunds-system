package com.xfunds.config;

import com.xfunds.common.SecurityUtils;
import com.xfunds.entity.FxUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 登录认证拦截器
 * 校验请求头中的 token 是否有效，放行登录与健康检查接口
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    /** 放行路径前缀 */
    private static final String[] WHITELIST = {
            "/api/auth/login",
            "/api/health",
            "/api/menu-nav"
    };

    /**
     * 请求前置处理：校验 token
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String uri = request.getRequestURI();
        // 白名单放行
        for (String path : WHITELIST) {
            if (uri.startsWith(path)) {
                return true;
            }
        }
        // 校验 token
        String token = SecurityUtils.getTokenFromRequest();
        if (token == null) {
            response.setStatus(200);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"未登录或登录已过期\",\"data\":null}");
            return false;
        }
        FxUser user = SecurityUtils.getUserByToken(token);
        if (user == null) {
            response.setStatus(200);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"登录已过期，请重新登录\",\"data\":null}");
            return false;
        }
        return true;
    }
}
