package com.xfunds.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 即期交易录入请求 DTO
 */
@Data
public class SpotTradeEntryRequest {

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

    /** 交易方向：BUY/SELL */
    @NotBlank(message = "交易方向不能为空")
    private String tradeDirection;

    /** 名义金额 */
    @NotNull(message = "名义金额不能为空")
    private BigDecimal notionalAmount;

    /** 客户汇率 */
    @NotNull(message = "客户汇率不能为空")
    private BigDecimal customerRate;

    /** 成本汇率 */
    private BigDecimal costRate;

    /** 即期汇率 */
    private BigDecimal spotRate;

    /** 分行利润点 */
    private BigDecimal branchProfitPoint;

    /** 起息日 */
    @NotNull(message = "起息日不能为空")
    private LocalDate valueDate;

    /** 交易日 */
    @NotNull(message = "交易日不能为空")
    private LocalDate tradeDate;

    /** 交割类型：T0/T1/T2 */
    @NotBlank(message = "交割类型不能为空")
    private String deliveryType;

    /** 货币1账户 */
    private String currency1Account;

    /** 货币2账户 */
    private String currency2Account;

    /** 保证金账户ID */
    private String marginAccountId;

    /** 保证金金额 */
    private BigDecimal marginAmount;

    /** 用途编码 */
    private String purposeCode;
}
