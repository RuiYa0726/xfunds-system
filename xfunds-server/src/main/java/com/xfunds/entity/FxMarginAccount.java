package com.xfunds.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 保证金账户实体
 */
@Data
public class FxMarginAccount {

    /** 保证金账户ID */
    private String marginAccountId;
    /** 客户ID */
    private String customerId;
    /** 币种 */
    private String currency;
    /** 账户余额 */
    private BigDecimal balance;
    /** 冻结金额 */
    private BigDecimal frozenAmount;
    /** 占用金额 */
    private BigDecimal occupiedAmount;
    /** 状态：1启用 0停用 */
    private String status;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 更新时间 */
    private LocalDateTime updatedAt;
}
