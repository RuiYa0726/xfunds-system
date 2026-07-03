package com.xfunds.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 授信额度保存请求 DTO
 */
@Data
public class CreditLimitSaveRequest {

    /** 额度ID（为空时表示新增） */
    private Long limitId;

    /** 客户ID */
    private String customerId;

    /** 币种 */
    private String currency;

    /** 授信额度 */
    private BigDecimal creditLimitAmount;

    /** 已用额度 */
    private BigDecimal usedAmount;

    /** 状态：1启用 0停用 */
    private String status;
}
