package com.xfunds.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 期权行权请求 DTO
 */
@Data
public class OptionExerciseRequest {

    /** 交易ID */
    @NotBlank(message = "交易ID不能为空")
    private String tradeId;

    /** 行权日 */
    @NotNull(message = "行权日不能为空")
    private LocalDate exerciseDate;

    /** 参考汇率 */
    @NotNull(message = "参考汇率不能为空")
    private BigDecimal referenceRate;

    /** 交割账户 */
    private String settlementAccount;

    /** 备注 */
    private String remark;
}
