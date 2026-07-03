package com.xfunds.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色实体
 */
@Data
public class FxRole {

    /** 角色ID */
    private Long roleId;
    /** 角色编码 */
    private String roleCode;
    /** 角色名称 */
    private String roleName;
    /** 描述 */
    private String description;
    /** 创建时间 */
    private LocalDateTime createdAt;
}
