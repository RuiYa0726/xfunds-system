package com.xfunds.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 期权费交割请求 DTO
 */
@Data
public class OptionPremiumSettleRequest {

    /** 交易ID */
    @NotBlank(message = "交易ID不能为空")
    private String tradeId;

    /** 交割账户 */
    private String settlementAccount;

    /** 备注 */
    private String remark;
}
