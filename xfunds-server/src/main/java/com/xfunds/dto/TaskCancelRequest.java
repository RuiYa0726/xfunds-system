package com.xfunds.dto;

import lombok.Data;

/**
 * 任务取消请求 DTO
 */
@Data
public class TaskCancelRequest {

    /** 取消备注 */
    private String remark;
}
