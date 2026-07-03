package com.xfunds.entity;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 即期交易子表实体
 */
@Data
public class FxSpotTrade {

    /** 交易ID（关联交易主表） */
    private String tradeId;
    /** 交割类型：T0/T1/T2 */
    private String settlementType;
    /** 即期汇率 */
    private BigDecimal spotRate;
    /** 客户汇率 */
    private BigDecimal customerRate;
    /** 成本汇率 */
    private BigDecimal costRate;
    /** 货币1账户 */
    private String currency1Account;
    /** 货币2账户 */
    private String currency2Account;
    /** 保证金账户ID */
    private String marginAccountId;
    /** 保证金金额 */
    private BigDecimal marginAmount;
    /** 金额 */
    private BigDecimal amount;
}
