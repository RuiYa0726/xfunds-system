package com.xfunds.mapper;

import com.xfunds.entity.ReportFieldMeta;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 报表字段元数据 Mapper
 */
@Mapper
public interface ReportFieldMetaMapper {

    /** 查询所有启用字段，按 sort_order 排序 */
    List<ReportFieldMeta> selectAllEnabled();

    /** 按 field_code 列表批量查询（白名单校验用） */
    List<ReportFieldMeta> selectByFieldCodes(@Param("fieldCodes") List<String> fieldCodes);

    /** 按 field_code 单条查询 */
    ReportFieldMeta selectByFieldCode(@Param("fieldCode") String fieldCode);

    /**
     * 执行动态查询。
     * <p>
     * 安全保证：
     * - fromClause 由 Service 层基于白名单字段元数据拼装，不含外部输入
     * - selectClause、groupByClause 同上，均来自白名单 column_expr
     * - filters 的 column_expr 来自白名单，值用 #{} 预编译占位
     * - isAdmin 为 true 时跳过机构过滤；否则按 orgCode 注入数据权限
     */
    List<Map<String, Object>> executeDynamicQuery(
            @Param("selectClause") String selectClause,
            @Param("fromClause") String fromClause,
            @Param("groupByClause") String groupByClause,
            @Param("orderByClause") String orderByClause,
            @Param("filters") List<FilterExpr> filters,
            @Param("orgCode") String orgCode,
            @Param("isAdmin") boolean isAdmin,
            @Param("limit") Integer limit);

    /** 执行分析查询（同比/环比） */
    List<Map<String, Object>> executeAnalysisQuery(
            @Param("selectClause") String selectClause,
            @Param("fromClause") String fromClause,
            @Param("groupByClause") String groupByClause,
            @Param("orderByClause") String orderByClause,
            @Param("filters") List<FilterExpr> filters,
            @Param("orgCode") String orgCode,
            @Param("isAdmin") boolean isAdmin,
            @Param("limit") Integer limit,
            @Param("analysisType") String analysisType,
            @Param("currentStartDate") String currentStartDate,
            @Param("currentEndDate") String currentEndDate,
            @Param("compareStartDate") String compareStartDate,
            @Param("compareEndDate") String compareEndDate);

    /** 过滤表达式（值由 #{} 预编译） */
    class FilterExpr {
        public String columnExpr; // 如 t.trade_date（来自白名单）
        public String dataType;   // STRING/DECIMAL/DATE/DATETIME
        public String operator;   // EQ/NE/LT/LE/GT/GE/LIKE/BETWEEN/IN
        public String value;      // 单值
        public String value2;     // BETWEEN 用第二值
        public List<String> values; // IN 用列表
    }
}
