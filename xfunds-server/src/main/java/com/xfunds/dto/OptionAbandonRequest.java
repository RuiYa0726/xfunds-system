package com.xfunds.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 期权放弃请求 DTO
 */
@Data
public class OptionAbandonRequest {

    /** 交易ID */
    @NotBlank(message = "交易ID不能为空")
    private String tradeId;

    /** 放弃日 */
    @NotNull(message = "放弃日不能为空")
    private LocalDate abandonDate;

    /** 备注 */
    private String remark;
}
