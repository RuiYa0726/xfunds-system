package com.xfunds.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客户账户实体
 */
@Data
public class FxCustomerAccount {

    /** 账户ID */
    private Long accountId;
    /** 客户ID */
    private String customerId;
    /** 账号 */
    private String accountNo;
    /** 币种 */
    private String currency;
    /** 账户类型：SPOT现汇 CASH现钞 */
    private String accountType;
    /** 账户余额 */
    private BigDecimal balance;
    /** 账户折人民币余额（按当日即期汇率折算） */
    private BigDecimal cnyBalance;
    /** 冻结金额 */
    private BigDecimal frozenAmount;
    /** 状态：1启用 0停用 */
    private String status;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 更新时间 */
    private LocalDateTime updatedAt;
}
