package com.xfunds.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户实体
 */
@Data
public class FxCustomer {

    /** 客户ID */
    private String customerId;
    /** 客户名称 */
    private String customerName;
    /** 客户类型 */
    private String customerType;
    /** 证件类型 */
    private String idType;
    /** 证件号码 */
    private String idNo;
    /** 所属机构编码 */
    private String orgCode;
    /** 信用等级 */
    private String creditLevel;
    /** 状态：1启用 0停用 */
    private String status;
    /** 联系人 */
    private String contactPerson;
    /** 联系电话 */
    private String contactPhone;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 更新时间 */
    private LocalDateTime updatedAt;
}
