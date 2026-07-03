package com.xfunds.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 远期交易子表实体
 */
@Data
public class FxForwardTrade {

    /** 交易ID（关联交易主表） */
    private String tradeId;
    /** 到期日 */
    private LocalDate maturityDate;
    /** 远期汇率 */
    private BigDecimal forwardRate;
    /** 远期点 */
    private BigDecimal forwardPoint;
    /** 交割方式：FULL/NET */
    private String settlementMethod;
    /** 差额交割金额 */
    private BigDecimal netSettlementAmount;
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
    /** 是否展期 */
    private String isRolledOver;
    /** 原交易ID */
    private String originalTradeId;
    /** 提前交割标志 */
    private String earlyDeliveryFlag;
    /** 提前违约标志 */
    private String earlyDefaultFlag;
}
