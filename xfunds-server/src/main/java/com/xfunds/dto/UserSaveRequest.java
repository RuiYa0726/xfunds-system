package com.xfunds.dto;

import lombok.Data;

import java.util.List;

/**
 * 用户保存请求 DTO
 */
@Data
public class UserSaveRequest {

    /** 用户ID（为空时表示新增） */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 密码（新增时必填，更新时为空表示不修改） */
    private String password;

    /** 真实姓名 */
    private String realName;

    /** 所属机构编码 */
    private String orgCode;

    /** 状态：1启用 0停用 */
    private String status;

    /** 角色ID列表 */
    private List<Long> roleIds;
}
