package com.xfunds.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 保证金计算请求 DTO
 */
@Data
public class MarginCalcRequest {

    /** 金额 */
    @NotNull(message = "金额不能为空")
    private BigDecimal amount;

    /** 币种 */
    @NotBlank(message = "币种不能为空")
    private String currency;

    /** 交易类型：SPOT/FORWARD/SWAP/OPTION */
    @NotBlank(message = "交易类型不能为空")
    private String tradeType;
}
