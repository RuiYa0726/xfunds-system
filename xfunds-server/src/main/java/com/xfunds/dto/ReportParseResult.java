package com.xfunds.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 自然语言解析结果
 */
@Data
public class ReportParseResult {

    /** 解析出的字段编码列表 */
    private List<String> selectFields = new ArrayList<>();

    /** 解析出的过滤条件 */
    private List<ReportQueryRequest.FilterCondition> filters = new ArrayList<>();

    /** 解析出的分组字段 */
    private List<String> groupBy = new ArrayList<>();

    /** 解析出的聚合配置：fieldCode -> SUM/AVG/COUNT/MAX/MIN（LLM 解析时填充） */
    private Map<String, String> aggregations = new HashMap<>();

    /** 解析出的排序条件（LLM 解析时填充） */
    private List<ReportQueryRequest.OrderByItem> orderBy = new ArrayList<>();

    /** 解析摘要（展示给用户确认） */
    private String summary;

    /** 是否解析成功 */
    private boolean success;

    /** 未识别的词 */
    private List<String> unmatchedWords = new ArrayList<>();

    /** 解析来源：RULE（规则）/ LLM（大模型） */
    private String parseSource;

    /** 分析类型：NORMAL(普通查询)、YOY(同比)、MOM(环比) */
    private String analysisType;

    /** 分析时间粒度：DAY/ WEEK/ MONTH/ QUARTER/ YEAR */
    private String timeGranularity;

    /** 分析基准日期（用于计算同比/环比的对比时间段） */
    private LocalDate baseDate;
}
