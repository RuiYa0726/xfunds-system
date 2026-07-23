package com.xfunds.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 期权交易录入请求 DTO
 */
@Data
public class OptionTradeEntryRequest {

    /** 客户ID */
    @NotBlank(message = "客户ID不能为空")
    private String customerId;

    /** 客户名称 */
    private String customerName;

    /** 基础货币 */
    @NotBlank(message = "基础货币不能为空")
    private String baseCurrency;

    /** 报价货币 */
    @NotBlank(message = "报价货币不能为空")
    private String quoteCurrency;

    /** 货币对 */
    private String currencyPair;

    /** 货币1账户 */
    private String currency1Account;

    /** 货币2账户 */
    private String currency2Account;

    /** 期权费账户ID */
    private String premiumAccountId;

    /** 买卖方向：BUY/SELL（银行买入或卖出期权） */
    @NotBlank(message = "买卖方向不能为空")
    private String buyerSeller;

    /** 期权类型：CALL/PUT */
    @NotBlank(message = "期权类型不能为空")
    private String optionType;

    /** 价格方向：UP/DOWN（第一货币涨/跌） */
    @NotBlank(message = "价格方向不能为空")
    private String priceDirection;

    /** 即期汇率 */
    @NotNull(message = "即期汇率不能为空")
    private BigDecimal spotRate;

    /** 执行价 */
    @NotNull(message = "执行价不能为空")
    private BigDecimal strikePrice;

    /** 行权方式：AMERICAN/EUROPEAN */
    @NotBlank(message = "行权方式不能为空")
    private String optionStyle;

    /** 行权时点 */
    private LocalDateTime exerciseTimePoint;

    /** 交易日 */
    @NotNull(message = "交易日不能为空")
    private LocalDate tradeDate;

    /** 到期日 */
    @NotNull(message = "到期日不能为空")
    private LocalDate maturityDate;

    /** 交割类型：T0/T1/T2 */
    @NotBlank(message = "交割类型不能为空")
    private String deliveryType;

    /** 交割日 */
    private LocalDate deliveryDate;

    /** 天数 */
    private Integer days;

    /** 期权费交割日 */
    private LocalDate premiumValueDate;

    /** 交割方式：FULL/NET */
    @NotBlank(message = "交割方式不能为空")
    private String settlementMethod;

    /** 面值（币种1）：交易金额 */
    @NotNull(message = "面值不能为空")
    private BigDecimal notionalAmount;

    /** 期权费金额 */
    private BigDecimal premiumAmount;

    /** 期权费币种 */
    private String premiumCurrency;

    /** 观察开始日 */
    private LocalDate observationStartDate;

    /** 观察结束日 */
    private LocalDate observationEndDate;

    /** 分行编码 */
    private String branchCode;

    /** 用途编码 */
    private String purposeCode;

    /** 外汇用途编码 */
    private String fxPurposeCode;
}
