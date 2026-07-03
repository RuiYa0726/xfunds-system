package com.xfunds.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 定时任务执行日志实体
 */
@Data
public class FxScheduledJobLog {

    /** 日志ID */
    private Long logId;
    /** 任务名称 */
    private String jobName;
    /** 触发类型：AUTO 自动 / MANUAL 手动 */
    private String triggerType;
    /** 运行时间 */
    private LocalDateTime runTime;
    /** 处理总数 */
    private Integer totalCount;
    /** 成功数 */
    private Integer successCount;
    /** 失败数 */
    private Integer failCount;
    /** 执行状态：SUCCESS 全部成功 / PARTIAL 部分失败 / FAILED 异常 */
    private String status;
    /** 错误信息 */
    private String errorMessage;
    /** 执行耗时（毫秒） */
    private Long durationMs;
    /** 触发操作人ID（手动触发时为登录用户，自动触发时为系统用户） */
    private Long operatorId;
    /** 触发操作人名称（关联 fx_user.real_name，仅查询时使用） */
    private String operatorName;
    /** 创建时间 */
    private LocalDateTime createdAt;
}
