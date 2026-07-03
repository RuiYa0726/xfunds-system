package com.xfunds.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 任务完成请求 DTO
 */
@Data
public class TaskCompleteRequest {

    /** 处理结果：APPROVE/REJECT/RETURN */
    @NotBlank(message = "处理结果不能为空")
    private String result;

    /** 交易ID */
    @NotBlank(message = "交易ID不能为空")
    private String tradeId;

    /** 审批意见 */
    private String comment;
}
