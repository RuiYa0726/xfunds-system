package com.xfunds.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 期权交易子表实体
 */
@Data
public class FxOptionTrade {

    /** 交易ID（关联交易主表） */
    private String tradeId;
    /** 期权类型：CALL/PUT */
    private String optionType;
    /** 行权方式：AMERICAN/EUROPEAN */
    private String optionStyle;
    /** 执行价 */
    private BigDecimal strikePrice;
    /** 期权费金额 */
    private BigDecimal premiumAmount;
    /** 期权费币种 */
    private String premiumCurrency;
    /** 期权费起息日 */
    private LocalDate premiumValueDate;
    /** 期权费是否已支付 */
    private String premiumPaidFlag;
    /** 期权费账户ID */
    private String premiumAccountId;
    /** 到期日 */
    private LocalDate maturityDate;
    /** 行权日 */
    private LocalDate exerciseDate;
    /** 是否行权标志 */
    private String exerciseFlag;
    /** 是否放弃标志 */
    private String abandonFlag;
    /** 交割方式：FULL/NET */
    private String settlementMethod;
    /** 买卖方向：买权/卖权 */
    private String buyerSeller;
    /** 货币1账户 */
    private String currency1Account;
    /** 货币2账户 */
    private String currency2Account;
    /** 名义金额 */
    private BigDecimal notionalAmount;
    /** 货币1金额 */
    private BigDecimal currency1Amount;
    /** 货币2金额 */
    private BigDecimal currency2Amount;
    /** 观察开始日 */
    private LocalDate observationStartDate;
    /** 观察结束日 */
    private LocalDate observationEndDate;
    /** 行权时间点 */
    private LocalDateTime exerciseTimePoint;
    /** 天数 */
    private Integer days;
    /** 是否自动行权 */
    private String autoExerciseFlag;
    /** 平仓日 */
    private LocalDate closeDate;
    /** 平仓期权费 */
    private BigDecimal closePremium;
    /** 平仓盈亏 */
    private BigDecimal closePnl;
    /** 已平仓金额 */
    private BigDecimal closedAmount;
    /** 剩余金额 */
    private BigDecimal remainingAmount;
    /** 参考汇率 */
    private BigDecimal referenceRate;
}
