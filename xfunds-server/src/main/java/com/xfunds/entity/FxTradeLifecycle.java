package com.xfunds.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交易生命周期事件实体
 */
@Data
public class FxTradeLifecycle {

    /** 事件ID */
    private Long eventId;
    /** 交易ID */
    private String tradeId;
    /** 事件类型 */
    private String eventType;
    /** 事件时间 */
    private LocalDateTime eventTime;
    /** 操作人ID */
    private Long operatorId;
    /** 变更前状态 */
    private String beforeStatus;
    /** 变更后状态 */
    private String afterStatus;
    /** 事件金额 */
    private BigDecimal eventAmount;
    /** 事件汇率 */
    private BigDecimal eventRate;
    /** 关联交易ID */
    private String relatedTradeId;
    /** 备注 */
    private String remark;
    /** 审计快照 */
    private String auditSnapshot;
}
