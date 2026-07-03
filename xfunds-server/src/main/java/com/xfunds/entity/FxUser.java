package com.xfunds.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体
 */
@Data
public class FxUser {

    /** 用户ID */
    private Long userId;
    /** 用户名 */
    private String username;
    /** 密码 */
    private String password;
    /** 真实姓名 */
    private String realName;
    /** 所属机构编码 */
    private String orgCode;
    /** 状态：1启用 0停用 */
    private String status;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 更新时间 */
    private LocalDateTime updatedAt;
}
