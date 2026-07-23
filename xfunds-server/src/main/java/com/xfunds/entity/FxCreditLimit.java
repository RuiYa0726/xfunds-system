package com.xfunds.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客户授信额度实体
 */
@Data
public class FxCreditLimit {

    /** 额度ID */
    private Long limitId;
    /** 客户ID */
    private String customerId;
    /** 币种 */
    private String currency;
    /** 授信额度 */
    private BigDecimal creditLimitAmount;
    /** 已用额度 */
    private BigDecimal usedAmount;
    /** 可用额度 */
    private BigDecimal availableAmount;
    /** 状态：1启用 0停用 */
    private String status;
    /** 更新时间 */
    private LocalDateTime updatedAt;
}
