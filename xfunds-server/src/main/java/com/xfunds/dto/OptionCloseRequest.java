package com.xfunds.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 期权平仓请求 DTO
 */
@Data
public class OptionCloseRequest {

    /** 交易ID */
    @NotBlank(message = "交易ID不能为空")
    private String tradeId;

    /** 平仓日 */
    @NotNull(message = "平仓日不能为空")
    private LocalDate closeDate;

    /** 平仓金额 */
    @NotNull(message = "平仓金额不能为空")
    private BigDecimal closeAmount;

    /** 平仓期权费 */
    private BigDecimal closePremium;

    /** 平仓盈亏 */
    private BigDecimal closePnl;

    /** 交割账户 */
    private String settlementAccount;

    /** 备注 */
    private String remark;
}
