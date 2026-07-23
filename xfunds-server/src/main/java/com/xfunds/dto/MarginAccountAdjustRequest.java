package com.xfunds.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 保证金账户余额人工调整请求 DTO
 */
@Data
public class MarginAccountAdjustRequest {

    /** 保证金账户ID */
    @NotBlank(message = "保证金账户ID不能为空")
    private String marginAccountId;

    /** 调整后的新余额 */
    @NotNull(message = "新余额不能为空")
    private BigDecimal newBalance;

    /** 调整备注（用于流水记录） */
    private String remark;
}
