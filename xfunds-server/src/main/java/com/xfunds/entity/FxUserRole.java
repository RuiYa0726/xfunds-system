package com.xfunds.entity;

import lombok.Data;

/**
 * 用户角色关联实体
 */
@Data
public class FxUserRole {

    /** 用户ID */
    private Long userId;
    /** 角色ID */
    private Long roleId;
}
