package com.xfunds.dto;

import lombok.Data;

import java.util.List;

/**
 * 登录响应 DTO
 */
@Data
public class LoginResponse {

    /** 访问令牌 */
    private String token;

    /** 用户ID */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 真实姓名 */
    private String realName;

    /** 所属机构编码 */
    private String orgCode;

    /** 角色编码列表 */
    private List<String> roles;
}
