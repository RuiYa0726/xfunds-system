package com.xfunds.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 审批请求 DTO（用于复核通过/拒绝/退回）
 */
@Data
public class ApprovalRequest {

    /** 任务ID */
    @NotNull(message = "任务ID不能为空")
    private Long taskId;

    /** 交易ID */
    @NotBlank(message = "交易ID不能为空")
    private String tradeId;

    /** 审批意见 */
    private String comment;
}
