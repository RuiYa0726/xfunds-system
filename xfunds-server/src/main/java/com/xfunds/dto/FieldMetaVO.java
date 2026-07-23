package com.xfunds.dto;

import lombok.Data;

/**
 * 字段元数据展示 DTO（前端字段面板用）
 */
@Data
public class FieldMetaVO {

    private String fieldCode;
    private String displayName;
    private String dataType;
    private String category;
    private Integer sortOrder;
    private String isDimension;
    private String isMetric;
    private String isFilterable;
    private String defaultAgg;
    private Integer sensitiveLevel;
}
