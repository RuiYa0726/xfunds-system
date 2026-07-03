package com.xfunds.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批日志实体
 */
@Data
public class FxApprovalLog {

    /** 日志ID */
    private Long logId;
    /** 交易ID */
    private String tradeId;
    /** 审批节点：MAKE/CHECK/AUTHORIZE */
    private String approvalNode;
    /** 审批人ID */
    private Long approverId;
    /** 审批人姓名 */
    private String approverName;
    /** 审批人机构 */
    private String approverOrg;
    /** 决定：APPROVE/REJECT/RETURN */
    private String decision;
    /** 决定时间 */
    private LocalDateTime decisionTime;
    /** 审批意见 */
    private String comment;
    /** 变更前状态 */
    private String beforeStatus;
    /** 变更后状态 */
    private String afterStatus;
}
