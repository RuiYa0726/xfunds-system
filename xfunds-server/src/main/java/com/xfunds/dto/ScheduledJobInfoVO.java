package com.xfunds.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 定时任务信息 VO
 */
@Data
public class ScheduledJobInfoVO {

    /** 任务名称 */
    private String jobName;
    /** 任务描述 */
    private String description;
    /** cron 表达式 */
    private String cronExpression;
    /** 执行频率描述 */
    private String scheduleDesc;
    /** 是否启用 */
    private Boolean enabled;
    /** 下次执行时间 */
    private LocalDateTime nextRunTime;
    /** 最近一次执行时间 */
    private LocalDateTime latestRunTime;
    /** 最近一次执行状态 */
    private String latestRunStatus;
    /** 最近一次执行摘要（如：处理 10 笔，成功 8 笔，失败 2 笔） */
    private String latestRunSummary;
}
