package com.xfunds.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务视图对象（含交易主表关联信息）
 */
@Data
public class TaskVO {

    /** 任务ID */
    private Long taskId;
    /** 任务类型 */
    private String taskType;
    /** 交易ID */
    private String tradeId;
    /** 业务编号（来自交易主表） */
    private String businessNo;
    /** 交易类型（来自交易主表） */
    private String tradeType;
    /** 业务键 */
    private String businessKey;
    /** 业务类型 */
    private String businessType;
    /** 任务状态 */
    private String status;
    /** 优先级 */
    private Integer priority;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 到期时间 */
    private LocalDateTime dueTime;
    /** 指派人ID */
    private Long assigneeId;
    /** 指派角色 */
    private String assigneeRole;
    /** 任务载荷（JSON） */
    private String payload;
    /** 经办人姓名 */
    private String makerName;
    /** 经办人ID */
    private Long makerId;
    /** 受理人姓名 */
    private String assigneeName;
}
