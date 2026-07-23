package com.xfunds.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 报表查询结果
 */
@Data
public class ReportQueryResult {

    /** 列定义（有序）：field_code -> 中文显示名 */
    private List<ColumnDef> columns;

    /** 数据行（每行为 field_code -> value 的 Map） */
    private List<Map<String, Object>> rows;

    /** 总行数 */
    private Integer total;

    /** 解析来源说明（NLP 模式下展示给用户确认） */
    private String parseSummary;

    /** 列定义 */
    @Data
    public static class ColumnDef {
        private String fieldCode;
        private String displayName;
        private String dataType;
        private String aggFunc;
    }
}
