package com.xfunds.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 掉期交易录入请求 DTO
 */
@Data
public class SwapTradeEntryRequest {

    /** 客户ID */
    @NotBlank(message = "客户ID不能为空")
    private String customerId;

    /** 分行编码 */
    @NotBlank(message = "分行编码不能为空")
    private String branchCode;

    /** 基础货币 */
    @NotBlank(message = "基础货币不能为空")
    private String baseCurrency;

    /** 报价货币 */
    @NotBlank(message = "报价货币不能为空")
    private String quoteCurrency;

    /** 掉期类型：S_B近卖远买 / B_S近买远卖 */
    @NotBlank(message = "掉期类型不能为空")
    private String swapType;

    /** 近端方向 */
    @NotBlank(message = "近端方向不能为空")
    private String nearLegDirection;

    /** 近端金额 */
    @NotNull(message = "近端金额不能为空")
    private BigDecimal nearLegAmount;

    /** 近端汇率 */
    @NotNull(message = "近端汇率不能为空")
    private BigDecimal nearLegRate;

    /** 近端成本汇率 */
    private BigDecimal nearLegCostRate;

    /** 近端客户汇率 */
    private BigDecimal nearLegCustomerRate;

    /** 近端分行收益点 */
    private BigDecimal nearLegBranchProfitPoint;

    /** 近端起息日 */
    @NotNull(message = "近端起息日不能为空")
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
    @NotBlank(message = "远端方向不能为空")
    private String farLegDirection;

    /** 远端金额 */
    @NotNull(message = "远端金额不能为空")
    private BigDecimal farLegAmount;

    /** 远端汇率 */
    @NotNull(message = "远端汇率不能为空")
    private BigDecimal farLegRate;

    /** 远端成本汇率 */
    private BigDecimal farLegCostRate;

    /** 远端客户汇率 */
    private BigDecimal farLegCustomerRate;

    /** 远端分行收益点 */
    private BigDecimal farLegBranchProfitPoint;

    /** 远端起息日 */
    @NotNull(message = "远端起息日不能为空")
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

    /** 客户汇率 */
    private BigDecimal customerRate;

    /** 成本汇率 */
    private BigDecimal costRate;

    /** 交易日 */
    @NotNull(message = "交易日不能为空")
    private LocalDate tradeDate;

    /** 保证金账户ID */
    private String marginAccountId;

    /** 保证金金额 */
    private BigDecimal marginAmount;

    /** 用途编码 */
    private String purposeCode;

    /** 外汇用途编码 */
    private String fxPurposeCode;
}
