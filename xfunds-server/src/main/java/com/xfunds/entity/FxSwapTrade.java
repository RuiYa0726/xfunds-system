package com.xfunds.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 掉期交易子表实体
 */
@Data
public class FxSwapTrade {

    /** 交易ID（关联交易主表） */
    private String tradeId;
    /** 掉期类型：S_B近卖远买 / B_S近买远卖 */
    private String swapType;
    /** 近端方向 */
    private String nearLegDirection;
    /** 近端金额 */
    private BigDecimal nearLegAmount;
    /** 近端汇率 */
    private BigDecimal nearLegRate;
    /** 近端成本汇率 */
    private BigDecimal nearLegCostRate;
    /** 近端客户汇率 */
    private BigDecimal nearLegCustomerRate;
    /** 近端分行收益点 */
    private BigDecimal nearLegBranchProfitPoint;
    /** 近端起息日 */
    private LocalDate nearLegValueDate;
    /** 近端账户 */
    private String nearLegAccount;
    /** 近端币种1账户 */
    private String nearLegCurrency1Account;
    /** 近端币种2账户 */
    private String nearLegCurrency2Account;
    /** 近端交割方式：FULL/NET */
    private String nearLegSettlementMethod;
    /** 远端方向 */
    private String farLegDirection;
    /** 远端金额 */
    private BigDecimal farLegAmount;
    /** 远端汇率 */
    private BigDecimal farLegRate;
    /** 远端成本汇率 */
    private BigDecimal farLegCostRate;
    /** 远端客户汇率 */
    private BigDecimal farLegCustomerRate;
    /** 远端分行收益点 */
    private BigDecimal farLegBranchProfitPoint;
    /** 远端起息日 */
    private LocalDate farLegValueDate;
    /** 远端账户 */
    private String farLegAccount;
    /** 远端币种1账户 */
    private String farLegCurrency1Account;
    /** 远端币种2账户 */
    private String farLegCurrency2Account;
    /** 远端交割方式：FULL/NET */
    private String farLegSettlementMethod;
    /** 掉期期限：ON/TN/SN/SW/1M */
    private String term;
    /** 掉期点 */
    private BigDecimal swapPoint;
    /** 近端即期汇率 */
    private BigDecimal nearSpotRate;
    /** 是否纯掉期 */
    private String isPureSwap;
    /** 保证金账户ID */
    private String marginAccountId;
    /** 保证金金额 */
    private BigDecimal marginAmount;
}
