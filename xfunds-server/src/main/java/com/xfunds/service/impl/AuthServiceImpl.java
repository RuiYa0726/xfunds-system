package com.xfunds.service.impl;

import com.xfunds.common.BusinessException;
import com.xfunds.common.Constants;
import com.xfunds.common.ResultCode;
import com.xfunds.common.SecurityUtils;
import com.xfunds.dto.LoginRequest;
import com.xfunds.dto.LoginResponse;
import com.xfunds.entity.FxOrg;
import com.xfunds.entity.FxRole;
import com.xfunds.entity.FxUser;
import com.xfunds.mapper.FxOrgMapper;
import com.xfunds.mapper.FxRoleMapper;
import com.xfunds.mapper.FxUserMapper;
import com.xfunds.mapper.FxUserRoleMapper;
import com.xfunds.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 认证服务实现类
 */
@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private FxUserMapper fxUserMapper;

    @Autowired
    private FxUserRoleMapper fxUserRoleMapper;

    @Autowired
    private FxOrgMapper fxOrgMapper;

    @Autowired
    private FxRoleMapper fxRoleMapper;

    /**
     * 用户登录：校验用户名密码，生成 token 并缓存用户信息
     */
    @Override
    public LoginResponse login(LoginRequest request) {
        // 根据用户名查询用户
        FxUser user = fxUserMapper.selectByUsername(request.getUsername());
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        // 明文密码校验
        if (!user.getPassword().equals(request.getPassword())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        // 校验用户状态
        if (!Constants.STATUS_ACTIVE.equals(user.getStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "用户已被停用，请联系管理员");
        }
        // 查询用户角色编码列表
        List<String> roles = fxUserRoleMapper.selectRoleCodesByUserId(user.getUserId());
        // 生成 token（去掉横线）
        String token = UUID.randomUUID().toString().replace("-", "");
        // 缓存 token 与用户信息
        SecurityUtils.putToken(token, user, roles);
        // 构建登录响应
        return buildLoginResponse(token, user, roles);
    }

    /**
     * 获取当前登录用户信息
     */
    @Override
    public LoginResponse getUserInfo() {
        FxUser user = SecurityUtils.getCurrentUser();
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录或登录已过期");
        }
        List<String> roles = SecurityUtils.getRolesByToken(SecurityUtils.getTokenFromRequest());
        return buildLoginResponse(SecurityUtils.getTokenFromRequest(), user, roles);
    }

    /**
     * 构建登录响应：填充用户基本信息、机构名称、角色名称
     */
    private LoginResponse buildLoginResponse(String token, FxUser user, List<String> roles) {
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUserId(user.getUserId());
        response.setUsername(user.getUsername());
        response.setRealName(user.getRealName());
        response.setOrgCode(user.getOrgCode());
        response.setRoles(roles);
        // 填充机构名称
        if (user.getOrgCode() != null) {
            FxOrg org = fxOrgMapper.selectByOrgCode(user.getOrgCode());
            response.setOrgName(org != null ? org.getOrgName() : user.getOrgCode());
        }
        // 填充角色名称
        List<String> roleNames = new ArrayList<>();
        if (roles != null) {
            for (String roleCode : roles) {
                FxRole role = fxRoleMapper.selectByRoleCode(roleCode);
                roleNames.add(role != null ? role.getRoleName() : roleCode);
            }
        }
        response.setRoleNames(roleNames);
        return response;
    }

    /**
     * 登出：移除当前 token 缓存
     */
    @Override
    public void logout() {
        String token = SecurityUtils.getTokenFromRequest();
        if (token != null) {
            SecurityUtils.removeToken(token);
        }
    }
}
