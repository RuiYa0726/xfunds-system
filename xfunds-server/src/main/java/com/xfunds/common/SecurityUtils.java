package com.xfunds.common;

import com.xfunds.entity.FxUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.Map;

/**
 * 登录上下文工具类
 * 采用简易 Token 机制：登录时生成 token 并缓存用户信息，请求头携带 Authorization: Bearer {token}
 */
public class SecurityUtils {

    /** token -> 用户信息的内存缓存（简易实现，生产环境应替换为 Redis） */
    private static final Map<String, FxUser> TOKEN_CACHE = new HashMap<>();

    /** token -> 用户角色编码列表缓存 */
    private static final Map<String, java.util.List<String>> TOKEN_ROLE_CACHE = new HashMap<>();

    /**
     * 缓存登录用户信息与 token 的映射
     */
    public static void putToken(String token, FxUser user, java.util.List<String> roles) {
        TOKEN_CACHE.put(token, user);
        TOKEN_ROLE_CACHE.put(token, roles);
    }

    /**
     * 移除 token 缓存（登出时调用）
     */
    public static void removeToken(String token) {
        TOKEN_CACHE.remove(token);
        TOKEN_ROLE_CACHE.remove(token);
    }

    /**
     * 根据 token 获取用户信息
     */
    public static FxUser getUserByToken(String token) {
        return TOKEN_CACHE.get(token);
    }

    /**
     * 根据 token 获取角色编码列表
     */
    public static java.util.List<String> getRolesByToken(String token) {
        return TOKEN_ROLE_CACHE.getOrDefault(token, java.util.Collections.emptyList());
    }

    /**
     * 从当前请求中提取 token
     */
    public static String getTokenFromRequest() {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            return null;
        }
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        return null;
    }

    /**
     * 获取当前登录用户
     */
    public static FxUser getCurrentUser() {
        String token = getTokenFromRequest();
        if (token == null) {
            return null;
        }
        return TOKEN_CACHE.get(token);
    }

    /**
     * 获取当前登录用户ID
     */
    public static Long getCurrentUserId() {
        FxUser user = getCurrentUser();
        return user == null ? null : user.getUserId();
    }

    /**
     * 获取当前登录用户机构编码
     */
    public static String getCurrentOrgCode() {
        FxUser user = getCurrentUser();
        return user == null ? null : user.getOrgCode();
    }

    /**
     * 判断当前用户是否拥有指定角色
     */
    public static boolean hasRole(String roleCode) {
        String token = getTokenFromRequest();
        if (token == null) {
            return false;
        }
        return TOKEN_ROLE_CACHE.getOrDefault(token, java.util.Collections.emptyList()).contains(roleCode);
    }

    /**
     * 判断当前用户是否同时拥有 MAKER 和 CHECKER 角色
     */
    public static boolean hasBothMakerAndCheckerRoles() {
        String token = getTokenFromRequest();
        if (token == null) {
            return false;
        }
        java.util.List<String> roles = TOKEN_ROLE_CACHE.getOrDefault(token, java.util.Collections.emptyList());
        return roles.contains(com.xfunds.enums.RoleCode.MAKER.name()) 
            && roles.contains(com.xfunds.enums.RoleCode.CHECKER.name());
    }

    /**
     * 判断当前用户是否为系统管理员（admin）
     * 系统管理员可经办、复核、跨机构操作
     */
    public static boolean isAdmin() {
        FxUser user = getCurrentUser();
        return user != null && "admin".equals(user.getUsername());
    }

    /**
     * 获取当前 HTTP 请求对象
     */
    private static HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getRequest();
    }

    private SecurityUtils() {
    }
}
