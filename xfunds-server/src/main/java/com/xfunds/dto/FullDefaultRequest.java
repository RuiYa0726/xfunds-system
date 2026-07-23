package com.xfunds.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 全部违约请求 DTO（掉期近端未到期）
 * 包含抵消掉期交易的录入字段，用于生成与原交易方向相反的新掉期交易
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FullDefaultRequest extends SwapTradeEntryRequest {

    /** 原交易ID */
    @NotBlank(message = "交易ID不能为空")
    private String tradeId;

    /** 违约金金额 */
    private BigDecimal penaltyAmount;

    /** 违约金扣减的保证金账户ID */
    private String penaltyAccount;

    /** 备注 */
    private String remark;
}
