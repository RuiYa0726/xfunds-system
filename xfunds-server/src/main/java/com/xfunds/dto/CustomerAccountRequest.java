package com.xfunds.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 客户账户请求 DTO
 */
@Data
public class CustomerAccountRequest {

    /** 账户ID（更新时必填） */
    private Long accountId;

    /** 账号 */
    @NotBlank(message = "账号不能为空")
    private String accountNo;

    /** 币种 */
    @NotBlank(message = "币种不能为空")
    private String currency;

    /** 账户类型：SPOT现汇 CASH现钞 */
    private String accountType;

    /** 账户余额 */
    private BigDecimal balance;

    /** 冻结金额 */
    private BigDecimal frozenAmount;

    /** 状态：1启用 0停用 */
    private String status;
}
