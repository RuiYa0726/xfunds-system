package com.xfunds.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 远期交易录入请求 DTO（继承即期交易基础字段，扩展远期专属字段）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ForwardTradeEntryRequest extends SpotTradeEntryRequest {

    /** 到期日 */
    @NotNull(message = "到期日不能为空")
    private LocalDate maturityDate;

    /** 交割方式：FULL/NET */
    @NotBlank(message = "交割方式不能为空")
    private String settlementMethod;
}
