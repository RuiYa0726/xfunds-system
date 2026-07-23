package com.xfunds.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 保证金增补请求 DTO
 */
@Data
public class MarginSupplementRequest {

    /** 交易ID */
    @NotBlank(message = "交易ID不能为空")
    private String tradeId;

    /** 增补金额 */
    @NotNull(message = "增补金额不能为空")
    private BigDecimal supplementAmount;

    /** 保证金账户ID */
    @NotBlank(message = "保证金账户ID不能为空")
    private String marginAccountId;

    /** 备注 */
    private String remark;
}
