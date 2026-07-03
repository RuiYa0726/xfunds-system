package com.xfunds.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务实体
 */
@Data
public class FxTask {

    /** 任务ID */
    private Long taskId;
    /** 任务类型 */
    private String taskType;
    /** 交易ID */
    private String tradeId;
    /** 业务键 */
    private String businessKey;
    /** 业务类型 */
    private String businessType;
    /** 优先级 */
    private Integer priority;
    /** 指派人ID */
    private Long assigneeId;
    /** 指派角色 */
    private String assigneeRole;
    /** 指派机构 */
    private String assigneeOrg;
    /** 指派机构层级 */
    private Integer assigneeOrgLevel;
    /** 状态：PENDING/CLAIMED/DONE/CANCELLED/TIMEOUT */
    private String status;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 到期时间 */
    private LocalDateTime dueTime;
    /** 认领时间 */
    private LocalDateTime claimTime;
    /** 完成时间 */
    private LocalDateTime completeTime;
    /** 认领锁 */
    private String claimLock;
    /** 任务载荷（JSON） */
    private String payload;
}
