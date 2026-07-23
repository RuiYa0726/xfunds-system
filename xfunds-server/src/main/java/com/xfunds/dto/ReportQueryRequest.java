package com.xfunds.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 报表查询请求
 */
@Data
public class ReportQueryRequest {

    /** 查询字段编码列表（用户选择/解析后的字段） */
    private List<String> selectFields;

    /** 聚合配置：按字段编码设置聚合方式，如 {"notional_amount":"SUM"} */
    private java.util.Map<String, String> aggregations;

    /** 分组字段列表 */
    private List<String> groupBy;

    /** 过滤条件列表 */
    private List<FilterCondition> filters;

    /** 排序条件列表 */
    private List<OrderByItem> orderBy;

    /** 来源：NLP 自然语言 / SELECT 勾选 */
    private String querySource;

    /** 限制返回行数（导出时不限制） */
    private Integer limit;

    /** 分析类型：NORMAL(普通查询)、YOY(同比)、MOM(环比) */
    private String analysisType;

    /** 分析基准日期（用于计算同比/环比的对比时间段） */
    private LocalDate baseDate;

    /** 分析时间粒度：DAY/ WEEK/ MONTH/ QUARTER/ YEAR */
    private String timeGranularity;

    /**
     * 过滤条件
     */
    @Data
    public static class FilterCondition {
        /** 字段编码 */
        private String fieldCode;
        /** 操作符：EQ/NE/LT/LE/GT/GE/LIKE/BETWEEN/IN */
        private String operator;
        /** 单值 */
        private String value;
        /** BETWEEN 第二值 */
        private String value2;
        /** IN 列表 */
        private List<String> values;
    }

    /**
     * 排序条件
     */
    @Data
    public static class OrderByItem {
        /** 字段编码 */
        private String fieldCode;
        /** 排序方向：ASC 升序 / DESC 降序 */
        private String direction;
    }
}
