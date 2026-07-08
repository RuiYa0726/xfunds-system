package com.xfunds.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

/**
 * 期权放弃请求 DTO
 * 放弃日（abandonDate）在审批通过时由后端自动设置（=审批通过日期），提交时无需传入
 */
@Data
public class OptionAbandonRequest {

    /** 交易ID */
    @NotBlank(message = "交易ID不能为空")
    private String tradeId;

    /** 放弃日（审批通过时由后端设置，提交时可为空） */
    private LocalDate abandonDate;

    /** 备注 */
    private String remark;
}
