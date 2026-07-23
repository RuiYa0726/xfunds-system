package com.xfunds.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 原价展期任务载荷
 * 发起原价展期时组装，复核通过后由 processRolloverOriginalApproval 解析执行
 */
@Data
public class RolloverOriginalTaskPayload {
    /** 原交易ID */
    private String originalTradeId;
    /** 原业务编号 */
    private String originalBusinessNo;
    /** 原交易类型 */
    private String originalTradeType;
    /** 客户ID */
    private String customerId;
    /** 客户名称 */
    private String customerName;
    /** 货币对 */
    private String currencyPair;
    /** 原交易方向 */
    private String originalTradeDirection;
    /** 原交易金额 */
    private BigDecimal originalAmount;
    /** 原交易汇率 */
    private BigDecimal originalCustomerRate;
    /** 原成本汇率 */
    private BigDecimal originalCostRate;
    /** 原到期日 */
    private LocalDate originalMaturityDate;
    /** 交易机构编码 */
    private String branchCode;
    /** 交易机构名称 */
    private String branchName;

    /** 远端新到期日 */
    private LocalDate newMaturityDate;
    /** 近端成本汇率（取原交易成本汇率） */
    private BigDecimal nearLegCostRate;
    /** 近端客户汇率（取原交易客户汇率） */
    private BigDecimal nearLegCustomerRate;
    /** 远端成本汇率（取原交易成本汇率） */
    private BigDecimal farLegCostRate;
    /** 远端客户汇率（取原交易客户汇率） */
    private BigDecimal farLegCustomerRate;
    /** 远端金额 */
    private BigDecimal farLegAmount;
    /** 远端分行收益点 */
    private BigDecimal farLegBranchProfitPoint;
    /** 远端币种1账户 */
    private String farLegCurrency1Account;
    /** 远端币种2账户 */
    private String farLegCurrency2Account;

    /** 备注 */
    private String remark;
}
