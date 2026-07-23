package com.xfunds.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 市价展期请求 DTO
 * 展期交易体现为一笔掉期交易：
 *   近端与原交易方向相反（平掉旧交易），远端与原交易方向相同（新远期交易）
 *   市价展期近端/远端成本汇率、客户汇率均取市场实时汇率，产生轧差
 *   客户买基准货币时：成本汇率取总/分S/B，客户汇率取分/客S/B
 *   客户卖基准货币时：成本汇率取总/分B/S，客户汇率取分/客B/S
 */
@Data
public class RolloverMarketRequest {

    /** 原交易ID */
    @NotBlank(message = "交易ID不能为空")
    private String tradeId;

    /** 远端新到期日（必须晚于原到期日） */
    @NotNull(message = "新到期日不能为空")
    private LocalDate newMaturityDate;

    /** 近端成本汇率（取市场实时汇率） */
    private BigDecimal nearLegCostRate;

    /** 近端客户汇率（取市场实时汇率） */
    private BigDecimal nearLegCustomerRate;

    /** 远端成本汇率（取市场实时汇率） */
    private BigDecimal farLegCostRate;

    /** 远端客户汇率（取市场实时汇率） */
    private BigDecimal farLegCustomerRate;

    /** 远端金额 */
    private BigDecimal farLegAmount;

    /** 远端分行收益点 */
    private BigDecimal farLegBranchProfitPoint;

    /** 远端币种1账户 */
    private String farLegCurrency1Account;

    /** 远端币种2账户 */
    private String farLegCurrency2Account;

    /** 轧差货币（默认CNY） */
    private String nettingCurrency;

    /** 轧差账户 */
    private String nettingAccount;

    /** 轧差金额（新客户汇率、原交易客户汇率、交易金额计算所得） */
    private BigDecimal nettingAmount;

    /** 客户损益 */
    private BigDecimal customerPnl;

    /** 备注 */
    private String remark;
}
