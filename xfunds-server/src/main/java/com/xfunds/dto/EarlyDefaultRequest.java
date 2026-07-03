package com.xfunds.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 提前违约请求 DTO
 */
@Data
public class EarlyDefaultRequest {

    /** 原交易ID */
    @NotBlank(message = "交易ID不能为空")
    private String tradeId;

    /** 违约金额 */
    @NotNull(message = "违约金额不能为空")
    private BigDecimal defaultAmount;

    /** 违约结算账户 */
    private String defaultAccount;

    /** 违约金金额 */
    private BigDecimal penaltyAmount;

    /** 违约金扣减的保证金账户ID */
    private String penaltyAccount;

    /** 备注 */
    private String remark;

    /** 即期交易市场汇率（用于反向即期交易） */
    private BigDecimal spotMarketRate;

    /** 即期客户汇率 */
    private BigDecimal spotCustomerRate;

    /** 掉期近端汇率（含违约惩罚） */
    private BigDecimal swapNearLegRate;

    /** 掉期近端起息日 */
    private LocalDate swapNearLegValueDate;

    /** 掉期近端成本汇率 */
    private BigDecimal swapNearLegCostRate;

    /** 掉期远端汇率（原交易汇率） */
    private BigDecimal swapFarLegRate;

    /** 掉期远端成本汇率 */
    private BigDecimal swapFarLegCostRate;

    /** 近端账户 */
    private String nearLegAccount;

    /** 远端账户 */
    private String farLegAccount;

    /** 惩罚汇率 */
    private BigDecimal penaltyRate;

    /** 轧差货币（默认CNY） */
    private String nettingCurrency;

    /** 轧差账户 */
    private String nettingAccount;
    
    /** 轧差金额 */
    private BigDecimal nettingAmount;
}
