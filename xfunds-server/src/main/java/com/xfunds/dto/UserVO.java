package com.xfunds.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户视图对象 DTO（不含密码）
 */
@Data
public class UserVO {

    /** 用户ID */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 真实姓名 */
    private String realName;

    /** 所属机构编码 */
    private String orgCode;

    /** 状态：1启用 0停用 */
    private String status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 角色编码列表 */
    private List<String> roles;

    /** 角色ID列表 */
    private List<Long> roleIds;
}
