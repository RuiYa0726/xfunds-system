package com.xfunds.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客户余额实体
 */
@Data
public class FxCustomerBalance {

    /** 余额ID */
    private Long balanceId;
    /** 客户ID */
    private String customerId;
    /** 币种 */
    private String currency;
    /** 余额金额 */
    private BigDecimal balanceAmount;
    /** 冻结金额 */
    private BigDecimal frozenAmount;
    /** 更新时间 */
    private LocalDateTime updatedAt;
}
