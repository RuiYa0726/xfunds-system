package com.xfunds.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 新增保证金账户请求 DTO
 */
@Data
public class MarginAccountCreateRequest {

    /** 币种 */
    @NotBlank(message = "币种不能为空")
    private String currency;

    /** 初始余额 */
    private BigDecimal initialBalance;
}
