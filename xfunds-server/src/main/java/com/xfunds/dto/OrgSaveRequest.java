package com.xfunds.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 机构保存请求 DTO
 */
@Data
public class OrgSaveRequest {

    /** 机构编码 */
    private String orgCode;

    /** 机构名称 */
    private String orgName;

    /** 机构层级：1总行 2分行 3支行 */
    private Integer orgLevel;

    /** 上级机构编码 */
    private String parentOrgCode;

    /** 机构类型 */
    private String orgType;

    /** 是否为交易机构：Y/N */
    private String isTradingOrg;

    /** 审批权限额度 */
    private BigDecimal approvalLimit;

    /** 外汇业务标志：Y/N */
    private String fxBusinessFlag;

    /** 状态：1启用 0停用 */
    private String status;
}
