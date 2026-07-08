package com.xfunds.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 期权工作台提醒视图对象
 */
@Data
public class OptionReminderVO {

    /** 交易ID */
    private String tradeId;

    /** 业务编号 */
    private String businessNo;

    /** 买卖方向：BUY/SELL */
    private String buyerSeller;

    /** 价格方向：UP/DOWN */
    private String priceDirection;

    /** 货币对 */
    private String currencyPair;

    /** 参考汇率（取交易即期汇率） */
    private BigDecimal referenceRate;

    /** 执行价 */
    private BigDecimal strikePrice;

    /** 期权状态 */
    private String optionStatus;

    /** 原始金额（名义金额） */
    private BigDecimal originalAmount;

    /** 已平仓金额 */
    private BigDecimal closedAmount;

    /** 剩余金额 */
    private BigDecimal remainingAmount;

    /** 观察开始日 */
    private LocalDate observationStartDate;

    /** 观察结束日 */
    private LocalDate observationEndDate;

    /** 交易日 */
    private LocalDate tradeDate;

    /** 到期日（欧式期权的到期日 / 美式期权的观察期结束日） */
    private LocalDate maturityDate;

    /** 客户ID */
    private String customerId;

    /** 客户名称 */
    private String customerName;

    /** 交割类型：T0/T1/T2 */
    private String deliveryType;

    /** 期权类型：CALL/PUT */
    private String optionType;

    /** 行权方式：AMERICAN/EUROPEAN */
    private String optionStyle;
}
