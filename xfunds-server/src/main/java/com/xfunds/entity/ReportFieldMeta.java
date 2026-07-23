package com.xfunds.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 报表字段元数据（白名单核心表）
 */
@Data
public class ReportFieldMeta {

    private Long id;
    /** 字段编码（程序内部唯一） */
    private String fieldCode;
    /** SQL 列表达式，如 t.trade_date */
    private String columnExpr;
    /** 中文显示名 */
    private String displayNameCn;
    /** STRING/DECIMAL/DATE/DATETIME */
    private String dataType;
    /** 是否维度 Y/N */
    private String isDimension;
    /** 是否指标 Y/N */
    private String isMetric;
    /** 是否可过滤 Y/N */
    private String isFilterable;
    /** 默认聚合函数：SUM/AVG/COUNT/MAX/MIN */
    private String defaultAgg;
    /** 来源表 */
    private String sourceTable;
    /** 前端分组：交易信息/币种金额/汇率/客户/机构/损益/期权 */
    private String category;
    /** 展示排序 */
    private Integer sortOrder;
    /** 是否启用 Y/N */
    private String enabled;
    /** 敏感等级 0普通 1机密 2绝密 */
    private Integer sensitiveLevel;
    private LocalDateTime createdAt;
}
