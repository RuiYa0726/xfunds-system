package com.xfunds.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 报表查询审计日志
 */
@Data
public class ReportQueryAuditLog {

    private Long logId;
    private Long userId;
    private String username;
    private String orgCode;
    /** 查询字段列表(JSON) */
    private String queryFields;
    /** 过滤条件(JSON) */
    private String filterConditions;
    private Integer rowCount;
    /** 来源：NLP 自然语言 / SELECT 勾选 */
    private String querySource;
    /** 执行耗时(毫秒) */
    private Long durationMs;
    private String ipAddress;
    private LocalDateTime createdAt;
}
