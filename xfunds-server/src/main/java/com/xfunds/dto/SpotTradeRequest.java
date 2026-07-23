package com.xfunds.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 即期交易请求 DTO
 */
@Data
public class SpotTradeRequest {

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

    /** 名义金额 */
    @NotNull(message = "名义金额不能为空")
    private BigDecimal notionalAmount;

    /** 交易方向：BUY/SELL */
    @NotBlank(message = "交易方向不能为空")
    private String tradeDirection;

    /** 交割类型：T0/T1/T2 */
    @NotBlank(message = "交割类型不能为空")
    private String deliveryType;

    /** 客户汇率 */
    @NotNull(message = "客户汇率不能为空")
    private BigDecimal customerRate;

    /** 起息日 */
    @NotNull(message = "起息日不能为空")
    private LocalDate valueDate;

    /** 用途编码 */
    private String purposeCode;
}
