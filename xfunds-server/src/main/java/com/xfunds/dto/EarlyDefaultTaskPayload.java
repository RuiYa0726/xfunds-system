package com.xfunds.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 提前违约任务载荷 DTO
 * 用于存储到 fx_task 表的 payload 字段中
 */
@Data
public class EarlyDefaultTaskPayload {

    /** 原交易ID */
    private String originalTradeId;
    /** 原业务编号 */
    private String originalBusinessNo;
    /** 原交易类型 */
    private String originalTradeType;
    /** 原客户ID */
    private String customerId;
    /** 原客户名称 */
    private String customerName;
    /** 原货币对 */
    private String currencyPair;
    /** 原交易方向 */
    private String originalTradeDirection;
    /** 原交易金额 */
    private BigDecimal originalAmount;
    /** 原交易汇率 */
    private BigDecimal originalCustomerRate;
    /** 原到期日 */
    private LocalDate originalMaturityDate;
    /** 违约金额 */
    private BigDecimal defaultAmount;
    /** 即期客户汇率 */
    private BigDecimal spotCustomerRate;
    /** 即期成本汇率 */
    private BigDecimal spotCostRate;
    /** 惩罚汇率 */
    private BigDecimal penaltyRate;
    /** 近端起息日 */
    private LocalDate swapNearLegValueDate;
    /** 掉期近端客户汇率 */
    private BigDecimal swapNearLegRate;
    /** 掉期近端成本汇率 */
    private BigDecimal swapNearLegCostRate;
    /** 掉期远端客户汇率 */
    private BigDecimal swapFarLegRate;
    /** 掉期远端成本汇率 */
    private BigDecimal swapFarLegCostRate;
    /** 近端账户 */
    private String nearLegAccount;
    /** 远端账户 */
    private String farLegAccount;
    /** 轧差货币 */
    private String nettingCurrency;
    /** 轧差账户 */
    private String nettingAccount;
    /** 轧差金额 */
    private BigDecimal nettingAmount;
    /** 备注 */
    private String remark;
}
