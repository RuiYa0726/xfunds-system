package com.xfunds.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 机构实体
 */
@Data
public class FxOrg {

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
    /** 是否为交易机构 */
    private String isTradingOrg;
    /** 审批权限额度 */
    private BigDecimal approvalLimit;
    /** 外汇业务标志 */
    private String fxBusinessFlag;
    /** 状态：1启用 0停用 */
    private String status;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 更新时间 */
    private LocalDateTime updatedAt;
}
