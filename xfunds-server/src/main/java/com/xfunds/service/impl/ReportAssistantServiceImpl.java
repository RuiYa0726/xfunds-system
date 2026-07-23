package com.xfunds.service.impl;

import com.alibaba.excel.EasyExcel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xfunds.common.SecurityUtils;
import com.xfunds.dto.FieldMetaVO;
import com.xfunds.dto.ReportParseResult;
import com.xfunds.dto.ReportQueryRequest;
import com.xfunds.dto.ReportQueryResult;
import com.xfunds.entity.FxUser;
import com.xfunds.entity.ReportFieldAlias;
import com.xfunds.entity.ReportFieldMeta;
import com.xfunds.entity.ReportQueryAuditLog;
import com.xfunds.mapper.ReportFieldAliasMapper;
import com.xfunds.mapper.ReportFieldMetaMapper;
import com.xfunds.mapper.ReportQueryAuditLogMapper;
import com.xfunds.service.ReportAssistantService;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.alibaba.excel.write.handler.SheetWriteHandler;
import com.alibaba.excel.write.metadata.holder.WriteSheetHolder;
import com.alibaba.excel.write.metadata.holder.WriteWorkbookHolder;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

/**
 * 报表助手服务实现
 * <p>
 * 安全原则：
 * 1. 字段白名单：所有可查询字段必须存在于 report_field_meta 表
 * 2. SQL 注入防护：SELECT/FROM/GROUP BY 子句的表达式来自白名单 column_expr，过滤值使用 MyBatis #{} 预编译
 * 3. 数据权限：非管理员自动注入 branch_code 过滤
 * 4. 敏感字段：sensitive_level=2 的字段禁止导出
 * 5. 审计日志：每次查询记录操作人、字段、条件、行数
 */
@Service
public class ReportAssistantServiceImpl implements ReportAssistantService {

    private static final Logger logger = LoggerFactory.getLogger(ReportAssistantServiceImpl.class);

    /** 最大返回行数（页面展示） */
    private static final int MAX_PAGE_ROWS = 1000;
    /** 最大导出行数 */
    private static final int MAX_EXPORT_ROWS = 100000;

    @Autowired
    private ReportFieldMetaMapper fieldMetaMapper;

    @Autowired
    private ReportFieldAliasMapper aliasMapper;

    @Autowired
    private ReportQueryAuditLogMapper auditLogMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LlmReportParser llmParser;

    /** 别名词典：aliasWord(小写) -> fieldCode（启动时加载，运行时只读） */
    private Map<String, String> aliasDictionary = new HashMap<>();
    /** 字段元数据缓存：fieldCode -> ReportFieldMeta */
    private Map<String, ReportFieldMeta> fieldMetaCache = new HashMap<>();

    @PostConstruct
    public void init() {
        reloadCache();
    }

    /** 重新加载字段元数据和别名词典缓存 */
    public void reloadCache() {
        List<ReportFieldMeta> fields = fieldMetaMapper.selectAllEnabled();
        Map<String, ReportFieldMeta> metaMap = new HashMap<>();
        for (ReportFieldMeta f : fields) {
            metaMap.put(f.getFieldCode(), f);
        }
        fieldMetaCache = metaMap;
        // 同步给 LLM 解析器
        llmParser.setFieldMetaCache(metaMap);

        List<ReportFieldAlias> aliases = aliasMapper.selectAll();
        Map<String, String> dict = new HashMap<>();
        for (ReportFieldAlias a : aliases) {
            dict.put(a.getAliasWord().toLowerCase(), a.getFieldCode());
        }
        aliasDictionary = dict;
        logger.info("报表助手缓存加载完成：字段 {} 个，别名 {} 个", metaMap.size(), dict.size());
    }

    // ===================== 字段查询 =====================

    @Override
    public Map<String, List<FieldMetaVO>> listFields() {
        List<ReportFieldMeta> fields = fieldMetaMapper.selectAllEnabled();
        Map<String, List<FieldMetaVO>> grouped = new LinkedHashMap<>();
        for (ReportFieldMeta f : fields) {
            FieldMetaVO vo = new FieldMetaVO();
            vo.setFieldCode(f.getFieldCode());
            vo.setDisplayName(f.getDisplayNameCn());
            vo.setDataType(f.getDataType());
            vo.setCategory(f.getCategory());
            vo.setSortOrder(f.getSortOrder());
            vo.setIsDimension(f.getIsDimension());
            vo.setIsMetric(f.getIsMetric());
            vo.setIsFilterable(f.getIsFilterable());
            vo.setDefaultAgg(f.getDefaultAgg());
            vo.setSensitiveLevel(f.getSensitiveLevel());
            grouped.computeIfAbsent(f.getCategory(), k -> new ArrayList<>()).add(vo);
        }
        return grouped;
    }

    // ===================== 自然语言解析 =====================

    @Override
    public ReportParseResult parseNaturalLanguage(String query) {
        logger.info("开始解析自然语言查询：{}", query);
        
        // 优先级1：LLM解析（最强大，处理复杂语义）
        try {
            ReportParseResult llmResult = llmParser.parse(query);
            if (llmResult.isSuccess() && !llmResult.getSelectFields().isEmpty()) {
                logger.info("LLM 解析成功：{}", llmResult.getSummary());
                // 检测分析类型：从用户输入中检测"同比"或"环比"关键词
                detectAnalysisTypeFromQuery(query, llmResult);
                // 重新解析日期范围：确保分析模式下使用正确的日期
                enhanceResultWithDate(query, llmResult);
                return llmResult;
            }
            logger.warn("LLM 解析未识别到字段");
        } catch (Exception e) {
            logger.warn("LLM 解析失败：{}", e.getMessage());
        }

        // 优先级2：模板匹配（快速匹配预定义场景）
        ReportParseResult templateResult = llmParser.tryMatchTemplate(query);
        if (templateResult != null && templateResult.isSuccess()) {
            logger.info("模板匹配成功：{}", templateResult.getSummary());
            addDateFiltersFromQuery(query, templateResult);
            return templateResult;
        }

        // 优先级3：规则解析（兜底，基于关键词匹配）
        ReportParseResult ruleResult = parseByRule(query);
        enhanceRuleResult(query, ruleResult);
        // 检测分析类型并重新解析日期
        detectAnalysisTypeFromQuery(query, ruleResult);
        enhanceResultWithDate(query, ruleResult);
        logger.info("规则解析结果：{}", ruleResult.getSummary());
        return ruleResult;
    }

    /**
     * 规则解析（原有逻辑）：基于别名词典 + 关键词匹配
     */
    private ReportParseResult parseByRule(String query) {
        ReportParseResult result = new ReportParseResult();
        result.setParseSource("RULE");
        if (query == null || query.trim().isEmpty()) {
            result.setSuccess(false);
            result.setSummary("输入为空");
            return result;
        }

        String normalized = query.trim();
        List<String> matchedFields = new ArrayList<>();
        List<ReportQueryRequest.FilterCondition> filters = new ArrayList<>();
        List<String> unmatched = new ArrayList<>();

        // 1. 识别交易类型
        String tradeType = detectTradeType(normalized);
        if (tradeType != null) {
            ReportQueryRequest.FilterCondition fc = new ReportQueryRequest.FilterCondition();
            fc.setFieldCode("trade_type");
            fc.setOperator("EQ");
            fc.setValue(tradeType);
            filters.add(fc);
        }

        // 1.1 识别分析类型（同比/环比）
        String analysisType = detectAnalysisType(normalized);
        if (analysisType != null) {
            result.setAnalysisType(analysisType);
        }

        // 1.2 识别时间粒度
        String timeGranularity = detectTimeGranularity(normalized);
        if (timeGranularity != null) {
            result.setTimeGranularity(timeGranularity);
        }

        // 2. 识别币种
        String[] currencies = detectCurrencies(normalized);
        if (currencies != null) {
            if (currencies[0] != null) {
                ReportQueryRequest.FilterCondition fc = new ReportQueryRequest.FilterCondition();
                fc.setFieldCode("base_currency");
                fc.setOperator("EQ");
                fc.setValue(currencies[0]);
                filters.add(fc);
            }
            if (currencies[1] != null) {
                ReportQueryRequest.FilterCondition fc = new ReportQueryRequest.FilterCondition();
                fc.setFieldCode("quote_currency");
                fc.setOperator("EQ");
                fc.setValue(currencies[1]);
                filters.add(fc);
            }
        }

        // 3. 识别日期范围
        ReportQueryRequest.FilterCondition[] dateFilters = detectDateRange(normalized);
        if (dateFilters[0] != null) filters.add(dateFilters[0]);
        if (dateFilters[1] != null) filters.add(dateFilters[1]);

        // 3.1 识别客户号（支持"客户XXX"、"客户号XXX"、"客户编号XXX"、"客户ID XXX"）
        String customerId = detectCustomerId(normalized);
        if (customerId != null) {
            ReportQueryRequest.FilterCondition fc = new ReportQueryRequest.FilterCondition();
            fc.setFieldCode("customer_id");
            fc.setOperator("LIKE");
            fc.setValue(customerId);
            filters.add(fc);
            // 从文本中移除已识别的客户号片段，避免"客户XXX"中的"客户"被别名误匹配为 customer_name 展示字段
            normalized = normalized.replaceAll("客户(?:号|编号|ID|id|Id)?\\s*" + Pattern.quote(customerId), " ");
        }

        // 4. 按别名词典匹配字段
        // 记录每个字段首次匹配到的别名词在原文中的位置，用于后续按输入顺序排序
        Map<String, Integer> fieldFirstPos = new HashMap<>();
        // 按词长度降序匹配（避免短词覆盖长词），长词匹配后从文本中移除，避免短词误匹配长词子串
        List<String> aliasWords = new ArrayList<>(aliasDictionary.keySet());
        aliasWords.sort((a, b) -> b.length() - a.length());

        Set<String> matched = new LinkedHashSet<>();
        String remainingText = normalized;
        for (String word : aliasWords) {
            int pos = remainingText.toLowerCase().indexOf(word.toLowerCase());
            if (pos >= 0) {
                String fieldCode = aliasDictionary.get(word);
                if (fieldMetaCache.containsKey(fieldCode)) {
                    matched.add(fieldCode);
                    // 记录字段在原文中首次出现的位置
                    fieldFirstPos.merge(fieldCode, pos, Math::min);
                    // 从文本中移除已匹配的词，避免短词误匹配长词子串
                    remainingText = remainingText.substring(0, pos)
                            + repeatSpaces(word.length())
                            + remainingText.substring(pos + word.length());
                }
            }
        }

        // 5. 过滤掉已作为过滤条件的字段（如交易类型、币种等已作过滤，不再出现在 selectFields）
        // 例外：trade_date、trade_type 可同时作为过滤条件和展示字段（用户明确要求时）
        Set<String> filterFieldCodes = filters.stream()
                .map(ReportQueryRequest.FilterCondition::getFieldCode)
                .collect(Collectors.toSet());
        for (String fc : matched) {
            // trade_date、trade_type 允许同时出现在过滤条件和展示字段中
            if (!filterFieldCodes.contains(fc) || "trade_date".equals(fc) || "trade_type".equals(fc)) {
                matchedFields.add(fc);
            }
        }

        // 6. 按用户输入文本中的出现顺序排序展示字段
        matchedFields.sort(Comparator.comparingInt(
                (String code) -> fieldFirstPos.getOrDefault(code, Integer.MAX_VALUE)));

        result.setSelectFields(matchedFields);
        result.setFilters(filters);
        result.setSuccess(true);

        // 生成解析摘要
        StringBuilder summary = new StringBuilder();
        if (!matchedFields.isEmpty()) {
            summary.append("展示字段：");
            summary.append(matchedFields.stream()
                    .map(code -> fieldMetaCache.get(code) != null ? fieldMetaCache.get(code).getDisplayNameCn() : code)
                    .collect(Collectors.joining("、")));
        }
        if (!filters.isEmpty()) {
            if (summary.length() > 0) summary.append("；");
            summary.append("过滤条件：");
            summary.append(filters.stream()
                    .map(this::describeFilter)
                    .collect(Collectors.joining("、")));
        }
        if (matchedFields.isEmpty() && filters.isEmpty()) {
            summary.append("未识别到有效字段或条件，建议使用字段勾选面板");
            result.setSuccess(false);
        }
        result.setSummary(summary.toString());
        result.setUnmatchedWords(unmatched);
        return result;
    }

    /** 描述过滤条件（用于解析摘要展示） */
    private String describeFilter(ReportQueryRequest.FilterCondition fc) {
        ReportFieldMeta meta = fieldMetaCache.get(fc.getFieldCode());
        String name = meta != null ? meta.getDisplayNameCn() : fc.getFieldCode();
        String op = switch (fc.getOperator()) {
            case "EQ" -> "=";
            case "NE" -> "!=";
            case "LT" -> "<";
            case "LE" -> "<=";
            case "GT" -> ">";
            case "GE" -> ">=";
            case "LIKE" -> "包含";
            case "BETWEEN" -> "介于";
            case "IN" -> "属于";
            default -> fc.getOperator();
        };
        if ("BETWEEN".equals(fc.getOperator())) {
            return name + " " + op + " " + fc.getValue() + " ~ " + fc.getValue2();
        }
        return name + " " + op + " " + fc.getValue();
    }

    /** 识别交易类型 */
    private String detectTradeType(String text) {
        if (text.contains("即期")) return "SPOT";
        if (text.contains("远期")) return "FORWARD";
        if (text.contains("掉期")) return "SWAP";
        if (text.contains("期权")) return "OPTION";
        return null;
    }

    /** 识别分析类型（同比/环比） */
    private String detectAnalysisType(String text) {
        if (text.contains("同比")) return "YOY";
        if (text.contains("环比")) return "MOM";
        return null;
    }

    /** 识别时间粒度（优先匹配更精确的粒度） */
    private String detectTimeGranularity(String text) {
        if (text.contains("日")) return "DAY";
        if (text.contains("周")) return "WEEK";
        if (text.contains("月") && !text.contains("季度")) return "MONTH";
        if (text.contains("季度")) return "QUARTER";
        if (text.contains("年")) return "YEAR";
        return "MONTH";
    }

    /** 识别币种，返回 [baseCurrency, quoteCurrency] */
    private String[] detectCurrencies(String text) {
        String base = null, quote = null;
        // 匹配 "USD/CNY" 或 "美元人民币"
        Pattern p = Pattern.compile("(USD|EUR|JPY|GBP|HKD|CNY|RMB)/?(USD|EUR|JPY|GBP|HKD|CNY|RMB)");
        Matcher m = p.matcher(text.toUpperCase());
        if (m.find()) {
            base = m.group(1);
            quote = "RMB".equals(m.group(2)) ? "CNY" : m.group(2);
        } else {
            if (text.contains("美元")) base = "USD";
            if (text.contains("人民币") || text.contains("人民币")) quote = "CNY";
            if (text.contains("欧元")) { base = "EUR"; }
            if (text.contains("日元")) { base = "JPY"; }
            if (text.contains("港币")) { base = "HKD"; }
            if (text.contains("英镑")) { base = "GBP"; }
        }
        if (base == null && quote == null) return null;
        return new String[]{base, quote};
    }

    /**
     * 识别客户号。支持模式：
     * - "客户号1111"、"客户编号1111"、"客户ID 1111"
     * - "客户1111"（客户后紧跟数字/字母组合，需排除"客户名称"、"客户类型"等字段词）
     * 返回客户号字符串（用于 LIKE 模糊匹配），未识别返回 null。
     */
    private String detectCustomerId(String text) {
        // 1. 优先匹配显式关键词："客户号"、"客户编号"、"客户ID" 后跟值
        //    值支持字母、数字、下划线、横杠，至少 2 位
        Pattern p1 = Pattern.compile("客户(?:号|编号|ID|id|Id)\\s*([A-Za-z0-9_\\-]{2,})");
        Matcher m1 = p1.matcher(text);
        if (m1.find()) {
            return m1.group(1);
        }
        // 2. "客户" 后紧跟数字/字母组合（至少 2 位），但需排除字段名（如"客户名称"、"客户类型"、"客户号"等）
        //    负向前瞻：客户后面不能紧跟 名称/姓名/类型/类别/号/编号/ID/信用/评级 等字段词
        Pattern p2 = Pattern.compile("客户(?!名称|姓名|类型|类别|号|编号|ID|id|Id|信用|评级|企业)([A-Za-z0-9_\\-]{2,})");
        Matcher m2 = p2.matcher(text);
        if (m2.find()) {
            return m2.group(1);
        }
        return null;
    }

    /** 识别日期范围，返回 [startDateFilter, endDateFilter] */
    private ReportQueryRequest.FilterCondition[] detectDateRange(String text) {
        ReportQueryRequest.FilterCondition startFc = null;
        ReportQueryRequest.FilterCondition endFc = null;

        // 匹配 "本月"
        if (text.contains("本月")) {
            LocalDate now = LocalDate.now();
            LocalDate start = LocalDate.of(now.getYear(), now.getMonth(), 1);
            LocalDate end = start.plusMonths(1).minusDays(1);
            startFc = buildDateFilter("trade_date", "GE", start.toString());
            endFc = buildDateFilter("trade_date", "LE", end.toString());
            return new ReportQueryRequest.FilterCondition[]{startFc, endFc};
        }

        // 匹配 "上月" / "上个月"
        if (text.contains("上月") || text.contains("上个月")) {
            LocalDate now = LocalDate.now();
            LocalDate start = LocalDate.of(now.getYear(), now.getMonth(), 1).minusMonths(1);
            LocalDate end = start.plusMonths(1).minusDays(1);
            startFc = buildDateFilter("trade_date", "GE", start.toString());
            endFc = buildDateFilter("trade_date", "LE", end.toString());
            return new ReportQueryRequest.FilterCondition[]{startFc, endFc};
        }

        // 匹配 "本周"
        if (text.contains("本周")) {
            LocalDate now = LocalDate.now();
            LocalDate start = now.with(java.time.DayOfWeek.MONDAY);
            LocalDate end = now.with(java.time.DayOfWeek.SUNDAY);
            startFc = buildDateFilter("trade_date", "GE", start.toString());
            endFc = buildDateFilter("trade_date", "LE", end.toString());
            return new ReportQueryRequest.FilterCondition[]{startFc, endFc};
        }

        // 匹配 "上周" / "上个星期"
        if (text.contains("上周") || text.contains("上个星期")) {
            LocalDate now = LocalDate.now();
            LocalDate start = now.with(java.time.DayOfWeek.MONDAY).minusWeeks(1);
            LocalDate end = now.with(java.time.DayOfWeek.SUNDAY).minusWeeks(1);
            startFc = buildDateFilter("trade_date", "GE", start.toString());
            endFc = buildDateFilter("trade_date", "LE", end.toString());
            return new ReportQueryRequest.FilterCondition[]{startFc, endFc};
        }

        // 匹配 "本季度"
        if (text.contains("本季度")) {
            LocalDate now = LocalDate.now();
            int quarter = (now.getMonthValue() - 1) / 3 + 1;
            LocalDate start = LocalDate.of(now.getYear(), (quarter - 1) * 3 + 1, 1);
            LocalDate end = start.plusMonths(3).minusDays(1);
            startFc = buildDateFilter("trade_date", "GE", start.toString());
            endFc = buildDateFilter("trade_date", "LE", end.toString());
            return new ReportQueryRequest.FilterCondition[]{startFc, endFc};
        }

        // 匹配 "上季度" / "上个季度"
        if (text.contains("上季度") || text.contains("上个季度")) {
            LocalDate now = LocalDate.now();
            int quarter = (now.getMonthValue() - 1) / 3 + 1;
            LocalDate start = LocalDate.of(now.getYear(), (quarter - 1) * 3 + 1, 1).minusMonths(3);
            LocalDate end = start.plusMonths(3).minusDays(1);
            startFc = buildDateFilter("trade_date", "GE", start.toString());
            endFc = buildDateFilter("trade_date", "LE", end.toString());
            return new ReportQueryRequest.FilterCondition[]{startFc, endFc};
        }

        // 匹配 "本年" / "今年"
        if (text.contains("本年") || text.contains("今年")) {
            LocalDate now = LocalDate.now();
            LocalDate start = LocalDate.of(now.getYear(), 1, 1);
            LocalDate end = LocalDate.of(now.getYear(), 12, 31);
            startFc = buildDateFilter("trade_date", "GE", start.toString());
            endFc = buildDateFilter("trade_date", "LE", end.toString());
            return new ReportQueryRequest.FilterCondition[]{startFc, endFc};
        }

        // 匹配 "去年" / "上年"
        if (text.contains("去年") || text.contains("上年")) {
            LocalDate now = LocalDate.now();
            int lastYear = now.getYear() - 1;
            startFc = buildDateFilter("trade_date", "GE", lastYear + "-01-01");
            endFc = buildDateFilter("trade_date", "LE", lastYear + "-12-31");
            return new ReportQueryRequest.FilterCondition[]{startFc, endFc};
        }

        // 匹配 "2026年6月" / "2026年06月"
        Pattern p1 = Pattern.compile("(\\d{4})年(\\d{1,2})月");
        Matcher m1 = p1.matcher(text);
        if (m1.find()) {
            int year = Integer.parseInt(m1.group(1));
            int month = Integer.parseInt(m1.group(2));
            LocalDate start = LocalDate.of(year, month, 1);
            LocalDate end = start.plusMonths(1).minusDays(1);
            startFc = buildDateFilter("trade_date", "GE", start.toString());
            endFc = buildDateFilter("trade_date", "LE", end.toString());
            return new ReportQueryRequest.FilterCondition[]{startFc, endFc};
        }

        // 匹配 "2026年"
        Pattern p2 = Pattern.compile("(\\d{4})年(?![\\d月])");
        Matcher m2 = p2.matcher(text);
        if (m2.find()) {
            int year = Integer.parseInt(m2.group(1));
            startFc = buildDateFilter("trade_date", "GE", year + "-01-01");
            endFc = buildDateFilter("trade_date", "LE", year + "-12-31");
            return new ReportQueryRequest.FilterCondition[]{startFc, endFc};
        }

        // 匹配 "最近N天" / "近N天"
        Pattern p3 = Pattern.compile("最近?(\\d+)天");
        Matcher m3 = p3.matcher(text);
        if (m3.find()) {
            int days = Integer.parseInt(m3.group(1));
            LocalDate end = LocalDate.now();
            LocalDate start = end.minusDays(days);
            startFc = buildDateFilter("trade_date", "GE", start.toString());
            endFc = buildDateFilter("trade_date", "LE", end.toString());
            return new ReportQueryRequest.FilterCondition[]{startFc, endFc};
        }

        // 匹配 "2026-06-01" 标准日期
        Pattern p4 = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");
        Matcher m4 = p4.matcher(text);
        if (m4.find()) {
            startFc = buildDateFilter("trade_date", "GE", m4.group(1));
            return new ReportQueryRequest.FilterCondition[]{startFc, null};
        }

        return new ReportQueryRequest.FilterCondition[]{null, null};
    }

    private ReportQueryRequest.FilterCondition buildDateFilter(String fieldCode, String op, String value) {
        ReportQueryRequest.FilterCondition fc = new ReportQueryRequest.FilterCondition();
        fc.setFieldCode(fieldCode);
        fc.setOperator(op);
        fc.setValue(value);
        return fc;
    }

    /** 生成指定长度的空格串（用于替换已匹配的文本，保持位置不变） */
    private String repeatSpaces(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) sb.append(' ');
        return sb.toString();
    }

    // ===================== 报表查询 =====================

    @Override
    public ReportQueryResult queryReport(ReportQueryRequest request) {
        long startTime = System.currentTimeMillis();
        ReportQueryResult result = new ReportQueryResult();

        if (request.getSelectFields() == null || request.getSelectFields().isEmpty()) {
            throw new IllegalArgumentException("请至少选择一个展示字段");
        }

        // 1. 白名单校验：所有字段必须存在于 report_field_meta
        List<ReportFieldMeta> selectedMetas = fieldMetaMapper.selectByFieldCodes(request.getSelectFields());
        if (selectedMetas.size() != request.getSelectFields().size()) {
            Set<String> found = selectedMetas.stream().map(ReportFieldMeta::getFieldCode).collect(Collectors.toSet());
            List<String> invalid = request.getSelectFields().stream()
                    .filter(code -> !found.contains(code))
                    .collect(Collectors.toList());
            throw new IllegalArgumentException("以下字段不在白名单中：" + String.join(", ", invalid));
        }

        // 按 selectFields 顺序排序
        Map<String, ReportFieldMeta> metaMap = selectedMetas.stream()
                .collect(Collectors.toMap(ReportFieldMeta::getFieldCode, f -> f));
        List<ReportFieldMeta> orderedMetas = new ArrayList<>();
        for (String code : request.getSelectFields()) {
            orderedMetas.add(metaMap.get(code));
        }

        // 2. 分析计算字段（yoy_*, mom_*）处理：
        //    - 分析模式下：保留用户勾选的分析字段，根据其类型生成对应的表达式
        //    - 普通模式下：过滤掉，因为这些字段的 column_expr 不是真实数据库列
        String analysisType = request.getAnalysisType();
        List<ReportFieldMeta> baseMetas;
        
        if ("YOY".equals(analysisType) || "MOM".equals(analysisType)) {
            baseMetas = orderedMetas;
        } else {
            baseMetas = orderedMetas.stream()
                    .filter(m -> !m.getFieldCode().startsWith("yoy_") && !m.getFieldCode().startsWith("mom_"))
                    .collect(Collectors.toList());
            if (orderedMetas.size() != baseMetas.size()) {
                logger.warn("普通查询模式下过滤了分析计算字段，请选择分析类型以查看同比/环比数据");
            }
        }

        // 3. 构建 SELECT 子句（含聚合）
        List<String> selectExprs = new ArrayList<>();
        boolean hasAggregation = false;
        List<String> autoGroupByExprs = new ArrayList<>();
        boolean hasExplicitAggregations = request.getAggregations() != null;
        
        for (ReportFieldMeta m : baseMetas) {
            String fieldCode = m.getFieldCode();
            String agg = null;
            if (hasExplicitAggregations) {
                agg = request.getAggregations().get(fieldCode);
                if (agg == null) {
                    agg = "NONE";
                }
            } else {
                agg = m.getDefaultAgg();
            }
            
            if (agg != null && !"NONE".equals(agg) && "Y".equals(m.getIsMetric())) {
                selectExprs.add(agg + "(" + m.getColumnExpr() + ") AS " + fieldCode);
                hasAggregation = true;
            } else {
                selectExprs.add(m.getColumnExpr() + " AS " + fieldCode);
                if (!fieldCode.startsWith("yoy_") && !fieldCode.startsWith("mom_")) {
                    autoGroupByExprs.add(m.getColumnExpr());
                }
            }
        }
        String selectClause = String.join(", ", selectExprs);

        // 4. 构建 GROUP BY 子句
        String groupByClause = "";
        if (request.getGroupBy() != null && !request.getGroupBy().isEmpty()) {
            List<ReportFieldMeta> groupMetas = fieldMetaMapper.selectByFieldCodes(request.getGroupBy());
            if (groupMetas.size() != request.getGroupBy().size()) {
                throw new IllegalArgumentException("分组字段不在白名单中");
            }
            // 显式指定的分组字段（按用户指定顺序），过滤掉分析计算字段
            LinkedHashSet<String> groupBySet = new LinkedHashSet<>();
            for (ReportFieldMeta gm : groupMetas) {
                if (!gm.getFieldCode().startsWith("yoy_") && !gm.getFieldCode().startsWith("mom_")) {
                    groupBySet.add(gm.getColumnExpr());
                }
            }
            // 有聚合时，自动补充其他未指定的非聚合维度字段（兼容 only_full_group_by）
            if (hasAggregation) {
                for (String expr : autoGroupByExprs) {
                    groupBySet.add(expr);
                }
            }
            groupByClause = String.join(", ", groupBySet);
        } else if (hasAggregation && !autoGroupByExprs.isEmpty()) {
            // 有聚合字段但未显式指定 GROUP BY：自动将非聚合字段加入 GROUP BY（兼容 only_full_group_by）
            groupByClause = String.join(", ", autoGroupByExprs);
        }

        // 5. 构建 FROM 子句（根据涉及表动态 JOIN）
        Set<String> involvedTables = new HashSet<>();
        involvedTables.add("fx_trade_master"); // 主表
        for (ReportFieldMeta m : baseMetas) {
            if (m.getSourceTable() != null) involvedTables.add(m.getSourceTable());
        }
        // 分组字段和过滤字段涉及的表也要加入
        if (request.getGroupBy() != null) {
            for (String code : request.getGroupBy()) {
                ReportFieldMeta m = fieldMetaCache.get(code);
                if (m != null && m.getSourceTable() != null) involvedTables.add(m.getSourceTable());
            }
        }
        if (request.getFilters() != null) {
            for (ReportQueryRequest.FilterCondition fc : request.getFilters()) {
                ReportFieldMeta m = fieldMetaCache.get(fc.getFieldCode());
                if (m != null && m.getSourceTable() != null) involvedTables.add(m.getSourceTable());
            }
        }
        String fromClause = buildFromClause(involvedTables);

        // 6. 构建过滤表达式
        List<ReportFieldMetaMapper.FilterExpr> filterExprs = new ArrayList<>();
        if (request.getFilters() != null) {
            for (ReportQueryRequest.FilterCondition fc : request.getFilters()) {
                if (("YOY".equals(analysisType) || "MOM".equals(analysisType)) && "trade_date".equals(fc.getFieldCode())) {
                    continue;
                }
                ReportFieldMeta m = fieldMetaCache.get(fc.getFieldCode());
                if (m == null) {
                    throw new IllegalArgumentException("过滤字段 " + fc.getFieldCode() + " 不在白名单");
                }
                ReportFieldMetaMapper.FilterExpr expr = new ReportFieldMetaMapper.FilterExpr();
                expr.columnExpr = m.getColumnExpr();
                expr.dataType = m.getDataType();
                expr.operator = fc.getOperator();
                expr.value = fc.getValue();
                expr.value2 = fc.getValue2();
                expr.values = fc.getValues();
                filterExprs.add(expr);
            }
        }

        // 6. 数据权限：非管理员注入机构过滤
        FxUser currentUser = SecurityUtils.getCurrentUser();
        boolean isAdmin = SecurityUtils.isAdmin();
        String orgCode = currentUser != null ? currentUser.getOrgCode() : null;

        // 6.1 构建排序子句（基于白名单 column_expr，安全）
        String orderByClause = "";
        if (request.getOrderBy() != null && !request.getOrderBy().isEmpty()) {
            List<String> orderParts = new ArrayList<>();
            for (ReportQueryRequest.OrderByItem item : request.getOrderBy()) {
                if (item.getFieldCode() == null || item.getFieldCode().isEmpty()) continue;
                ReportFieldMeta m = fieldMetaCache.get(item.getFieldCode());
                if (m == null) {
                    throw new IllegalArgumentException("排序字段 " + item.getFieldCode() + " 不在白名单");
                }
                String dir = "DESC".equalsIgnoreCase(item.getDirection()) ? "DESC" : "ASC";
                String orderExpr;
                if (isTermField(item.getFieldCode())) {
                    orderExpr = buildTermOrderExpr(m.getColumnExpr(), dir);
                } else {
                    orderExpr = m.getColumnExpr() + " " + dir;
                }
                orderParts.add(orderExpr);
            }
            if (!orderParts.isEmpty()) {
                orderByClause = String.join(", ", orderParts);
            }
        }

        // 7. 执行查询（区分普通查询和分析查询）
        Integer limit = request.getLimit() != null ? request.getLimit() : MAX_PAGE_ROWS;
        List<Map<String, Object>> rows;
        
        logger.info("SQL查询构建完成 - selectClause={}, fromClause={}, groupByClause={}, orderByClause={}, filterCount={}, hasAggregation={}", 
                selectClause, fromClause, groupByClause, orderByClause, 
                request.getFilters() != null ? request.getFilters().size() : 0, hasAggregation);
        
        if ("YOY".equals(analysisType) || "MOM".equals(analysisType)) {
            LocalDate baseDate = request.getBaseDate() != null ? request.getBaseDate() : LocalDate.now();
            String granularity = request.getTimeGranularity() != null ? request.getTimeGranularity() : "MONTH";
            
            Map<String, String> dateRange = calculateDateRange(analysisType, baseDate, granularity);
            String currentStartDate = dateRange.get("currentStart");
            String currentEndDate = dateRange.get("currentEnd");
            String compareStartDate = dateRange.get("compareStart");
            String compareEndDate = dateRange.get("compareEnd");
            
            String analysisSelectClause = buildAnalysisSelectClause(request, baseMetas, analysisType, 
                    currentStartDate, currentEndDate, compareStartDate, compareEndDate);
            
            String analysisOrderByClause = transformAnalysisOrderBy(request, baseMetas, orderByClause);
            
            rows = fieldMetaMapper.executeAnalysisQuery(
                    analysisSelectClause, fromClause, groupByClause, analysisOrderByClause, 
                    filterExprs, orgCode, isAdmin, limit, analysisType,
                    currentStartDate, currentEndDate, compareStartDate, compareEndDate);
            
            // 过滤掉所有指标字段都是0的行（本期和对比期都没有数据）
            rows = filterZeroDataRows(rows, baseMetas);
        } else {
            rows = fieldMetaMapper.executeDynamicQuery(
                    selectClause, fromClause, groupByClause, orderByClause, filterExprs, orgCode, isAdmin, limit);
        }

        // 8. 敏感字段脱敏（敏感_level=1 机密级）
        applyMasking(baseMetas, rows);

        // 9. 构建列定义（分析模式下使用分析字段名）
        List<ReportQueryResult.ColumnDef> columns = new ArrayList<>();
        if ("YOY".equals(analysisType) || "MOM".equals(analysisType)) {
            boolean hasAnalysisField = baseMetas.stream()
                    .anyMatch(m -> m.getFieldCode().startsWith("yoy_") || m.getFieldCode().startsWith("mom_"));
            
            for (ReportFieldMeta m : baseMetas) {
                String fieldCode = m.getFieldCode();
                if ("Y".equals(m.getIsMetric())) {
                    if (fieldCode.startsWith("yoy_current_") || fieldCode.startsWith("mom_current_")) {
                        ReportQueryResult.ColumnDef col = new ReportQueryResult.ColumnDef();
                        col.setFieldCode(fieldCode);
                        col.setDisplayName(m.getDisplayNameCn());
                        col.setDataType(m.getDataType());
                        String agg = request.getAggregations() != null ? request.getAggregations().get(fieldCode) : null;
                        col.setAggFunc(agg != null ? agg : m.getDefaultAgg());
                        columns.add(col);
                    } else if (fieldCode.startsWith("yoy_last_") || fieldCode.startsWith("mom_last_")) {
                        ReportQueryResult.ColumnDef col = new ReportQueryResult.ColumnDef();
                        col.setFieldCode(fieldCode);
                        col.setDisplayName(m.getDisplayNameCn());
                        col.setDataType(m.getDataType());
                        String agg = request.getAggregations() != null ? request.getAggregations().get(fieldCode) : null;
                        col.setAggFunc(agg != null ? agg : m.getDefaultAgg());
                        columns.add(col);
                    } else if (fieldCode.startsWith("yoy_rate_") || fieldCode.startsWith("mom_rate_")) {
                        ReportQueryResult.ColumnDef col = new ReportQueryResult.ColumnDef();
                        col.setFieldCode(fieldCode);
                        col.setDisplayName(m.getDisplayNameCn());
                        col.setDataType(m.getDataType());
                        col.setAggFunc(null);
                        columns.add(col);
                    } else {
                        ReportQueryResult.ColumnDef colCurrent = new ReportQueryResult.ColumnDef();
                        colCurrent.setFieldCode(fieldCode + "_current");
                        colCurrent.setDisplayName(m.getDisplayNameCn() + "(本期)");
                        colCurrent.setDataType(m.getDataType());
                        String agg = request.getAggregations() != null ? request.getAggregations().get(fieldCode) : null;
                        colCurrent.setAggFunc(agg != null ? agg : m.getDefaultAgg());
                        columns.add(colCurrent);

                        if (!hasAnalysisField) {
                            ReportQueryResult.ColumnDef colLast = new ReportQueryResult.ColumnDef();
                            colLast.setFieldCode(fieldCode + "_last");
                            String lastLabel = "YOY".equals(analysisType) ? m.getDisplayNameCn() + "(去年同期)" : m.getDisplayNameCn() + "(上期)";
                            colLast.setDisplayName(lastLabel);
                            colLast.setDataType(m.getDataType());
                            colLast.setAggFunc(colCurrent.getAggFunc());
                            columns.add(colLast);

                            ReportQueryResult.ColumnDef colRate = new ReportQueryResult.ColumnDef();
                            colRate.setFieldCode(fieldCode + "_rate");
                            String rateLabel = "YOY".equals(analysisType) ? "同比增长率(%)" : "环比增长率(%)";
                            colRate.setDisplayName(rateLabel);
                            colRate.setDataType("DECIMAL");
                            colRate.setAggFunc(null);
                            columns.add(colRate);
                        }
                    }
                } else {
                    ReportQueryResult.ColumnDef col = new ReportQueryResult.ColumnDef();
                    col.setFieldCode(fieldCode);
                    col.setDisplayName(m.getDisplayNameCn());
                    col.setDataType(m.getDataType());
                    col.setAggFunc(null);
                    columns.add(col);
                }
            }
        } else {
            for (ReportFieldMeta m : baseMetas) {
                ReportQueryResult.ColumnDef col = new ReportQueryResult.ColumnDef();
                col.setFieldCode(m.getFieldCode());
                col.setDisplayName(m.getDisplayNameCn());
                col.setDataType(m.getDataType());
                String agg = request.getAggregations() != null ? request.getAggregations().get(m.getFieldCode()) : null;
                col.setAggFunc(agg != null ? agg : m.getDefaultAgg());
                columns.add(col);
            }
        }

        result.setColumns(columns);
        result.setRows(rows);
        result.setTotal(rows.size());

        // 10. 审计日志
        long duration = System.currentTimeMillis() - startTime;
        writeAuditLog(request, rows.size(), duration);

        return result;
    }

    /** 构建 FROM 子句（根据涉及的表动态 JOIN） */
    private String buildFromClause(Set<String> involvedTables) {
        StringBuilder sb = new StringBuilder();
        sb.append("fx_trade_master t");
        if (involvedTables.contains("fx_spot_trade")) {
            sb.append(" LEFT JOIN fx_spot_trade st ON t.trade_id = st.trade_id");
        }
        if (involvedTables.contains("fx_forward_trade")) {
            sb.append(" LEFT JOIN fx_forward_trade ft ON t.trade_id = ft.trade_id");
        }
        if (involvedTables.contains("fx_swap_trade")) {
            sb.append(" LEFT JOIN fx_swap_trade sw ON t.trade_id = sw.trade_id");
        }
        if (involvedTables.contains("fx_option_trade")) {
            sb.append(" LEFT JOIN fx_option_trade ot ON t.trade_id = ot.trade_id");
        }
        if (involvedTables.contains("fx_customer")) {
            sb.append(" LEFT JOIN fx_customer c ON t.customer_id = c.customer_id");
        }
        if (involvedTables.contains("fx_org")) {
            sb.append(" LEFT JOIN fx_org o ON t.branch_code = o.org_code");
        }
        if (involvedTables.contains("fx_user")) {
            sb.append(" LEFT JOIN fx_user u ON t.maker_id = u.user_id");
        }
        return sb.toString();
    }

    /** 计算分析查询的日期范围 */
    private Map<String, String> calculateDateRange(String analysisType, LocalDate baseDate, String granularity) {
        Map<String, String> range = new HashMap<>();
        LocalDate currentStart, currentEnd, compareStart, compareEnd;
        
        switch (granularity) {
            case "DAY":
                currentStart = baseDate;
                currentEnd = baseDate;
                if ("YOY".equals(analysisType)) {
                    compareStart = baseDate.minusYears(1);
                    compareEnd = baseDate.minusYears(1);
                } else {
                    compareStart = baseDate.minusDays(1);
                    compareEnd = baseDate.minusDays(1);
                }
                break;
            case "WEEK":
                int dayOfWeek = baseDate.getDayOfWeek().getValue();
                currentStart = baseDate.minusDays(dayOfWeek - 1);
                currentEnd = currentStart.plusDays(6);
                if ("YOY".equals(analysisType)) {
                    compareStart = currentStart.minusYears(1);
                    compareEnd = currentEnd.minusYears(1);
                } else {
                    compareStart = currentStart.minusWeeks(1);
                    compareEnd = currentEnd.minusWeeks(1);
                }
                break;
            case "QUARTER":
                int quarter = (baseDate.getMonthValue() - 1) / 3 + 1;
                currentStart = LocalDate.of(baseDate.getYear(), (quarter - 1) * 3 + 1, 1);
                currentEnd = currentStart.plusMonths(3).minusDays(1);
                if ("YOY".equals(analysisType)) {
                    compareStart = currentStart.minusYears(1);
                    compareEnd = currentEnd.minusYears(1);
                } else {
                    compareStart = currentStart.minusMonths(3);
                    compareEnd = currentEnd.minusMonths(3);
                }
                break;
            case "YEAR":
                currentStart = LocalDate.of(baseDate.getYear(), 1, 1);
                currentEnd = LocalDate.of(baseDate.getYear(), 12, 31);
                if ("YOY".equals(analysisType)) {
                    compareStart = currentStart.minusYears(1);
                    compareEnd = currentEnd.minusYears(1);
                } else {
                    compareStart = currentStart.minusYears(1);
                    compareEnd = currentEnd.minusYears(1);
                }
                break;
            case "MONTH":
            default:
                currentStart = LocalDate.of(baseDate.getYear(), baseDate.getMonth(), 1);
                currentEnd = currentStart.plusMonths(1).minusDays(1);
                if ("YOY".equals(analysisType)) {
                    compareStart = currentStart.minusYears(1);
                    compareEnd = compareStart.plusMonths(1).minusDays(1);
                } else {
                    compareStart = currentStart.minusMonths(1);
                    compareEnd = compareStart.plusMonths(1).minusDays(1);
                }
                break;
        }
        
        range.put("currentStart", currentStart.toString());
        range.put("currentEnd", currentEnd.toString());
        range.put("compareStart", compareStart.toString());
        range.put("compareEnd", compareEnd.toString());
        
        return range;
    }

    /** 解析分析计算字段对应的基础字段名 */
    private String resolveBaseField(String shortName) {
        Map<String, String> fieldMapping = new HashMap<>();
        fieldMapping.put("notional", "notional_amount");
        fieldMapping.put("counter", "counter_amount");
        fieldMapping.put("profit", "branch_profit_point");
        
        if (fieldMapping.containsKey(shortName)) {
            return fieldMapping.get(shortName);
        }
        return shortName;
    }

    /** 构建分析查询的 SELECT 子句（同比/环比） */
    private String buildAnalysisSelectClause(ReportQueryRequest request, List<ReportFieldMeta> orderedMetas, 
            String analysisType, String currentStart, String currentEnd, String compareStart, String compareEnd) {
        List<String> selectExprs = new ArrayList<>();
        
        boolean hasAnalysisField = orderedMetas.stream()
                .anyMatch(m -> m.getFieldCode().startsWith("yoy_") || m.getFieldCode().startsWith("mom_"));
        
        for (ReportFieldMeta m : orderedMetas) {
            String agg = request.getAggregations() != null ? request.getAggregations().get(m.getFieldCode()) : null;
            if (agg == null || agg.isEmpty()) {
                agg = m.getDefaultAgg();
            }
            
            String columnExpr = m.getColumnExpr();
            String fieldCode = m.getFieldCode();
            
            if ("Y".equals(m.getIsMetric())) {
                String aggFunc = (agg != null && !"NONE".equals(agg)) ? agg : "SUM";
                
                if (fieldCode.startsWith("yoy_current_")) {
                    String baseField = resolveBaseField(fieldCode.replace("yoy_current_", ""));
                    ReportFieldMeta baseMeta = fieldMetaCache.get(baseField);
                    String baseExpr = baseMeta != null ? baseMeta.getColumnExpr() : "t.notional_amount";
                    selectExprs.add(aggFunc + "(CASE WHEN t.trade_date BETWEEN '" + currentStart + "' AND '" + currentEnd + "' THEN " + baseExpr + " ELSE 0 END) AS " + fieldCode);
                } else if (fieldCode.startsWith("mom_current_")) {
                    String baseField = resolveBaseField(fieldCode.replace("mom_current_", ""));
                    ReportFieldMeta baseMeta = fieldMetaCache.get(baseField);
                    String baseExpr = baseMeta != null ? baseMeta.getColumnExpr() : "t.notional_amount";
                    selectExprs.add(aggFunc + "(CASE WHEN t.trade_date BETWEEN '" + currentStart + "' AND '" + currentEnd + "' THEN " + baseExpr + " ELSE 0 END) AS " + fieldCode);
                } else if (fieldCode.startsWith("yoy_last_")) {
                    String baseField = resolveBaseField(fieldCode.replace("yoy_last_", ""));
                    ReportFieldMeta baseMeta = fieldMetaCache.get(baseField);
                    String baseExpr = baseMeta != null ? baseMeta.getColumnExpr() : "t.notional_amount";
                    selectExprs.add(aggFunc + "(CASE WHEN t.trade_date BETWEEN '" + compareStart + "' AND '" + compareEnd + "' THEN " + baseExpr + " ELSE 0 END) AS " + fieldCode);
                } else if (fieldCode.startsWith("mom_last_")) {
                    String baseField = resolveBaseField(fieldCode.replace("mom_last_", ""));
                    ReportFieldMeta baseMeta = fieldMetaCache.get(baseField);
                    String baseExpr = baseMeta != null ? baseMeta.getColumnExpr() : "t.notional_amount";
                    selectExprs.add(aggFunc + "(CASE WHEN t.trade_date BETWEEN '" + compareStart + "' AND '" + compareEnd + "' THEN " + baseExpr + " ELSE 0 END) AS " + fieldCode);
                } else if (fieldCode.startsWith("yoy_rate_")) {
                    String baseField = resolveBaseField(fieldCode.replace("yoy_rate_", ""));
                    ReportFieldMeta baseMeta = fieldMetaCache.get(baseField);
                    String baseExpr = baseMeta != null ? baseMeta.getColumnExpr() : "t.notional_amount";
                    selectExprs.add("ROUND((" + aggFunc + "(CASE WHEN t.trade_date BETWEEN '" + currentStart + "' AND '" + currentEnd + "' THEN " + baseExpr + " ELSE 0 END) - " + aggFunc + "(CASE WHEN t.trade_date BETWEEN '" + compareStart + "' AND '" + compareEnd + "' THEN " + baseExpr + " ELSE 0 END)) / NULLIF(" + aggFunc + "(CASE WHEN t.trade_date BETWEEN '" + compareStart + "' AND '" + compareEnd + "' THEN " + baseExpr + " ELSE 0 END), 0) * 100, 2) AS " + fieldCode);
                } else if (fieldCode.startsWith("mom_rate_")) {
                    String baseField = resolveBaseField(fieldCode.replace("mom_rate_", ""));
                    ReportFieldMeta baseMeta = fieldMetaCache.get(baseField);
                    String baseExpr = baseMeta != null ? baseMeta.getColumnExpr() : "t.notional_amount";
                    selectExprs.add("ROUND((" + aggFunc + "(CASE WHEN t.trade_date BETWEEN '" + currentStart + "' AND '" + currentEnd + "' THEN " + baseExpr + " ELSE 0 END) - " + aggFunc + "(CASE WHEN t.trade_date BETWEEN '" + compareStart + "' AND '" + compareEnd + "' THEN " + baseExpr + " ELSE 0 END)) / NULLIF(" + aggFunc + "(CASE WHEN t.trade_date BETWEEN '" + compareStart + "' AND '" + compareEnd + "' THEN " + baseExpr + " ELSE 0 END), 0) * 100, 2) AS " + fieldCode);
                } else {
                    if (hasAnalysisField) {
                        selectExprs.add(aggFunc + "(CASE WHEN t.trade_date BETWEEN '" + currentStart + "' AND '" + currentEnd + "' THEN " + columnExpr + " ELSE 0 END) AS " + fieldCode + "_current");
                    } else {
                        selectExprs.add(aggFunc + "(CASE WHEN t.trade_date BETWEEN '" + currentStart + "' AND '" + currentEnd + "' THEN " + columnExpr + " ELSE 0 END) AS " + fieldCode + "_current");
                        selectExprs.add(aggFunc + "(CASE WHEN t.trade_date BETWEEN '" + compareStart + "' AND '" + compareEnd + "' THEN " + columnExpr + " ELSE 0 END) AS " + fieldCode + "_last");
                        selectExprs.add("ROUND((" + aggFunc + "(CASE WHEN t.trade_date BETWEEN '" + currentStart + "' AND '" + currentEnd + "' THEN " + columnExpr + " ELSE 0 END) - " + aggFunc + "(CASE WHEN t.trade_date BETWEEN '" + compareStart + "' AND '" + compareEnd + "' THEN " + columnExpr + " ELSE 0 END)) / NULLIF(" + aggFunc + "(CASE WHEN t.trade_date BETWEEN '" + compareStart + "' AND '" + compareEnd + "' THEN " + columnExpr + " ELSE 0 END), 0) * 100, 2) AS " + fieldCode + "_rate");
                    }
                }
            } else {
                selectExprs.add(columnExpr + " AS " + fieldCode);
            }
        }
        
        return String.join(", ", selectExprs);
    }

    /** 转换分析模式下的排序字段名 */
    private String transformAnalysisOrderBy(ReportQueryRequest request, List<ReportFieldMeta> orderedMetas, String originalOrderBy) {
        if (originalOrderBy == null || originalOrderBy.isEmpty()) {
            return "";
        }
        
        String transformed = originalOrderBy;
        for (ReportFieldMeta m : orderedMetas) {
            if ("Y".equals(m.getIsMetric())) {
                String columnExpr = m.getColumnExpr();
                String fieldCode = m.getFieldCode();
                
                if (!fieldCode.startsWith("yoy_") && !fieldCode.startsWith("mom_")) {
                    transformed = transformed.replace(columnExpr + " ", fieldCode + "_current ");
                    transformed = transformed.replace(columnExpr + ",", fieldCode + "_current,");
                    transformed = transformed.replace(columnExpr + "\n", fieldCode + "_current\n");
                }
            }
        }
        
        return transformed;
    }

    /** 过滤掉所有指标字段都是0的行（本期和对比期都没有数据） */
    private List<Map<String, Object>> filterZeroDataRows(List<Map<String, Object>> rows, List<ReportFieldMeta> metas) {
        if (rows == null || rows.isEmpty()) {
            return rows;
        }
        
        List<String> metricFieldCodes = new ArrayList<>();
        for (ReportFieldMeta m : metas) {
            if ("Y".equals(m.getIsMetric())) {
                String fieldCode = m.getFieldCode();
                // 分析模式下指标字段会生成 *_current, *_last, *_rate 后缀
                if (!fieldCode.startsWith("yoy_") && !fieldCode.startsWith("mom_")) {
                    metricFieldCodes.add(fieldCode + "_current");
                    metricFieldCodes.add(fieldCode + "_last");
                } else {
                    metricFieldCodes.add(fieldCode);
                }
            }
        }
        
        return rows.stream()
                .filter(row -> row != null)
                .filter(row -> {
                    for (String fieldCode : metricFieldCodes) {
                        Object value = row.get(fieldCode);
                        if (value != null) {
                            if (value instanceof Number) {
                                if (((Number) value).doubleValue() != 0) {
                                    return true;
                                }
                            } else {
                                String strValue = value.toString().trim();
                                if (!strValue.isEmpty() && !strValue.equals("0") && !strValue.equals("0.0") && !strValue.equals("0.00")) {
                                    return true;
                                }
                            }
                        }
                    }
                    return false;
                })
                .collect(Collectors.toList());
    }

    /** 敏感字段脱敏：sensitive_level=1 的字段部分遮蔽 */
    private void applyMasking(List<ReportFieldMeta> metas, List<Map<String, Object>> rows) {
        for (ReportFieldMeta m : metas) {
            if (m.getSensitiveLevel() == null || m.getSensitiveLevel() < 1) continue;
            for (Map<String, Object> row : rows) {
                Object v = row.get(m.getFieldCode());
                if (v == null) continue;
                String s = v.toString();
                if (s.length() <= 4) {
                    row.put(m.getFieldCode(), "****");
                } else {
                    row.put(m.getFieldCode(), s.substring(0, 2) + "****" + s.substring(s.length() - 2));
                }
            }
        }
    }

    /** 写审计日志 */
    private void writeAuditLog(ReportQueryRequest request, int rowCount, long durationMs) {
        try {
            ReportQueryAuditLog log = new ReportQueryAuditLog();
            FxUser user = SecurityUtils.getCurrentUser();
            if (user != null) {
                log.setUserId(user.getUserId());
                log.setUsername(user.getUsername());
                log.setOrgCode(user.getOrgCode());
            }
            log.setQueryFields(objectMapper.writeValueAsString(request.getSelectFields()));
            log.setFilterConditions(objectMapper.writeValueAsString(request.getFilters()));
            log.setRowCount(rowCount);
            log.setQuerySource(request.getQuerySource() != null ? request.getQuerySource() : "SELECT");
            log.setDurationMs(durationMs);
            auditLogMapper.insert(log);
        } catch (Exception e) {
            logger.warn("审计日志写入失败", e);
        }
    }

    // ===================== Excel 导出 =====================

    @Override
    public void exportReport(ReportQueryRequest request, HttpServletResponse response) {
        request.setLimit(MAX_EXPORT_ROWS);
        ReportQueryResult result = queryReport(request);

        List<ReportQueryResult.ColumnDef> exportCols = result.getColumns().stream()
                .filter(col -> {
                    ReportFieldMeta m = fieldMetaCache.get(col.getFieldCode());
                    return m == null || m.getSensitiveLevel() == null || m.getSensitiveLevel() < 2;
                })
                .collect(Collectors.toList());

        if (exportCols.isEmpty()) {
            throw new IllegalArgumentException("没有可导出的字段（所有字段均为机密级，禁止导出）");
        }

        List<List<String>> head = new ArrayList<>();
        List<Integer> columnWidths = new ArrayList<>();
        for (ReportQueryResult.ColumnDef col : exportCols) {
            String displayName = col.getDisplayName() != null ? col.getDisplayName() : col.getFieldCode();
            
            head.add(Collections.singletonList(displayName));
            
            int width = Math.max(displayName.length() * 256, 10 * 256);
            if (isCurrencyColumn(col.getFieldCode()) || isRateColumn(col.getFieldCode())) {
                width = Math.max(width, 15 * 256);
            }
            columnWidths.add(width);
        }

        List<List<Object>> dataRows = new ArrayList<>();
        java.text.DecimalFormat currencyFormat = new java.text.DecimalFormat("#,##0.00");
        java.text.DecimalFormat rateFormat = new java.text.DecimalFormat("0.00");
        
        for (Map<String, Object> row : result.getRows()) {
            List<Object> rowData = new ArrayList<>();
            for (ReportQueryResult.ColumnDef col : exportCols) {
                Object v = row.get(col.getFieldCode());
                if (v == null) {
                    rowData.add("");
                } else if (v instanceof java.util.Date || v instanceof java.time.temporal.Temporal) {
                    rowData.add(v.toString());
                } else if (v instanceof BigDecimal) {
                    BigDecimal bd = (BigDecimal) v;
                    if (isRateColumn(col.getFieldCode())) {
                        rowData.add(rateFormat.format(bd));
                    } else if (isCurrencyColumn(col.getFieldCode())) {
                        rowData.add(currencyFormat.format(bd));
                    } else {
                        rowData.add(currencyFormat.format(bd));
                    }
                } else if (v instanceof Number) {
                    double num = ((Number) v).doubleValue();
                    if (isRateColumn(col.getFieldCode())) {
                        rowData.add(rateFormat.format(num));
                    } else if (isCurrencyColumn(col.getFieldCode())) {
                        rowData.add(currencyFormat.format(num));
                    } else {
                        rowData.add(currencyFormat.format(num));
                    }
                } else {
                    rowData.add(v);
                }
            }
            dataRows.add(rowData);
        }

        byte[] excelBytes;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            EasyExcel.write(baos)
                    .head(head)
                    .registerWriteHandler(new CustomSheetWriteHandler(columnWidths))
                    .sheet("报表数据")
                    .doWrite(dataRows);
            excelBytes = baos.toByteArray();
        } catch (Exception e) {
            logger.error("Excel 生成失败", e);
            throw new RuntimeException("导出失败：" + e.getMessage());
        }

        try {
            String fileName = "导出报表_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".xlsx";
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + encodedFileName);
            response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
            response.setContentLength(excelBytes.length);

            OutputStream out = response.getOutputStream();
            out.write(excelBytes);
            out.flush();
        } catch (Exception e) {
            logger.error("Excel 响应写入失败", e);
            throw new RuntimeException("导出失败：" + e.getMessage());
        }
    }

    private boolean isCurrencyColumn(String fieldCode) {
        return fieldCode != null && (
            fieldCode.equals("counter_amount") ||
            fieldCode.contains("_counter")
        );
    }

    private boolean isRateColumn(String fieldCode) {
        return fieldCode != null && fieldCode.contains("_rate");
    }

    private boolean isTermField(String fieldCode) {
        return fieldCode != null && (fieldCode.equals("forward_term") || fieldCode.equals("swap_term"));
    }

    private String buildTermOrderExpr(String columnExpr, String direction) {
        String termOrder = "FIELD(" + columnExpr + ", 'ON', 'TN', 'SN', 'SW', '1W', '2W', '1M', '2M', '3M', '6M', '9M', '1Y', '2Y', '3Y', '5Y', '10Y')";
        if ("DESC".equalsIgnoreCase(direction)) {
            return "-" + termOrder + " ASC";
        }
        return termOrder + " ASC";
    }

    private static class CustomSheetWriteHandler implements SheetWriteHandler {
        private final List<Integer> columnWidths;

        public CustomSheetWriteHandler(List<Integer> columnWidths) {
            this.columnWidths = columnWidths;
        }

        @Override
        public void beforeSheetCreate(WriteWorkbookHolder writeWorkbookHolder, WriteSheetHolder writeSheetHolder) {
        }

        @Override
        public void afterSheetCreate(WriteWorkbookHolder writeWorkbookHolder, WriteSheetHolder writeSheetHolder) {
            Sheet sheet = writeSheetHolder.getSheet();
            for (int i = 0; i < columnWidths.size(); i++) {
                sheet.setColumnWidth(i, columnWidths.get(i));
            }
        }
    }

    /** 字段码值字典（用于过滤条件值选择） */
    @Override
    public Map<String, List<OptionValue>> listFieldDict() {
        Map<String, List<OptionValue>> dict = new LinkedHashMap<>();
        // 交易类型
        dict.put("trade_type", Arrays.asList(
                new OptionValue("SPOT", "即期"),
                new OptionValue("FORWARD", "远期"),
                new OptionValue("SWAP", "掉期"),
                new OptionValue("OPTION", "期权")
        ));
        // 交易状态
        dict.put("status", Arrays.asList(
                new OptionValue("DRAFT", "草稿"),
                new OptionValue("PENDING_REVIEW", "待复核"),
                new OptionValue("COMPLETED", "已完成"),
                new OptionValue("CANCELLED", "已取消")
        ));
        // 交易方向
        dict.put("trade_direction", Arrays.asList(
                new OptionValue("BUY", "买入"),
                new OptionValue("SELL", "卖出")
        ));
        // 基础币种 / 报价币种 / 期权费币种 / 轧差货币
        List<OptionValue> currencies = Arrays.asList(
                new OptionValue("USD", "美元"),
                new OptionValue("CNY", "人民币"),
                new OptionValue("EUR", "欧元"),
                new OptionValue("JPY", "日元"),
                new OptionValue("HKD", "港币"),
                new OptionValue("GBP", "英镑"),
                new OptionValue("AUD", "澳元"),
                new OptionValue("SGD", "新加坡元")
        );
        dict.put("base_currency", currencies);
        dict.put("quote_currency", currencies);
        dict.put("premium_currency", currencies);
        dict.put("netting_currency", currencies);
        // 交割类型
        dict.put("delivery_type", Arrays.asList(
                new OptionValue("PHYSICAL", "全额交割"),
                new OptionValue("NETTING", "差额交割")
        ));
        // 交割方式
        dict.put("settlement_method", Arrays.asList(
                new OptionValue("SPOT", "即期交割"),
                new OptionValue("FORWARD", "远期交割")
        ));
        // 掉期类型
        dict.put("swap_type", Arrays.asList(
                new OptionValue("SPOT_FORWARD", "即期对远期"),
                new OptionValue("FORWARD_FORWARD", "远期对远期")
        ));
        // 期权类型
        dict.put("option_type", Arrays.asList(
                new OptionValue("CALL", "看涨期权"),
                new OptionValue("PUT", "看跌期权")
        ));
        // 是否行权
        dict.put("exercise_flag", boolOptions());
        // 是否展期 / 是否纯掉期 / 提前交割标志 / 提前违约标志
        dict.put("is_rolled_over", boolOptions());
        dict.put("is_pure_swap", boolOptions());
        dict.put("early_delivery_flag", boolOptions());
        dict.put("early_default_flag", boolOptions());
        // 客户类型
        dict.put("cust_customer_type", Arrays.asList(
                new OptionValue("CORPORATE", "企业客户"),
                new OptionValue("INDIVIDUAL", "个人客户"),
                new OptionValue("FINANCIAL", "金融机构")
        ));
        // 企业类型
        dict.put("cust_corp_type", Arrays.asList(
                new OptionValue("STATE_OWNED", "国有企业"),
                new OptionValue("PRIVATE", "民营企业"),
                new OptionValue("FOREIGN", "外资企业"),
                new OptionValue("LISTED", "上市公司")
        ));
        // 信用等级
        dict.put("cust_credit_level", Arrays.asList(
                new OptionValue("AAA", "AAA"),
                new OptionValue("AA", "AA"),
                new OptionValue("A", "A"),
                new OptionValue("BBB", "BBB"),
                new OptionValue("BB", "BB"),
                new OptionValue("B", "B")
        ));
        return dict;
    }

    private List<OptionValue> boolOptions() {
        return Arrays.asList(
                new OptionValue("Y", "是"),
                new OptionValue("N", "否")
        );
    }

    /**
     * 从用户输入中提取日期信息，添加到解析结果的过滤条件中
     */
    private void addDateFiltersFromQuery(String query, ReportParseResult result) {
        ReportQueryRequest.FilterCondition[] dateFilters = detectDateRange(query);
        if (dateFilters[0] != null) {
            boolean alreadyHasDate = result.getFilters().stream()
                    .anyMatch(f -> "trade_date".equals(f.getFieldCode()));
            if (!alreadyHasDate) {
                if (dateFilters[0] != null) result.getFilters().add(dateFilters[0]);
                if (dateFilters[1] != null) result.getFilters().add(dateFilters[1]);
            }
        }
    }

    /** 从用户输入中检测分析类型 */
    private void detectAnalysisTypeFromQuery(String query, ReportParseResult result) {
        if (query.contains("同比")) {
            result.setAnalysisType("YOY");
            logger.info("检测到同比关键词，设置分析类型为YOY");
        } else if (query.contains("环比")) {
            result.setAnalysisType("MOM");
            logger.info("检测到环比关键词，设置分析类型为MOM");
        }
    }

    /** 增强日期处理：分析模式下补充基准日期和时间粒度 */
    private void enhanceResultWithDate(String query, ReportParseResult result) {
        if ("YOY".equals(result.getAnalysisType()) || "MOM".equals(result.getAnalysisType())) {
            logger.info("分析模式：开始处理日期，当前分析类型={}", result.getAnalysisType());
            
            boolean hasDateFilter = false;
            for (ReportQueryRequest.FilterCondition fc : result.getFilters()) {
                if ("trade_date".equals(fc.getFieldCode())) {
                    hasDateFilter = true;
                    break;
                }
            }
            
            if (!hasDateFilter) {
                String[] dateRange = parseDateFromQuery(query);
                if (dateRange != null && dateRange[0] != null) {
                    ReportQueryRequest.FilterCondition fc = new ReportQueryRequest.FilterCondition();
                    fc.setFieldCode("trade_date");
                    fc.setOperator("BETWEEN");
                    fc.setValue(dateRange[0]);
                    fc.setValue2(dateRange[1]);
                    result.getFilters().add(fc);
                    logger.info("分析模式：从输入中解析日期范围 {} - {}", dateRange[0], dateRange[1]);
                } else {
                    logger.warn("分析模式：未能从输入中解析日期范围");
                }
            }
            
            if (result.getBaseDate() == null) {
                LocalDate baseDate = deriveBaseDate(query);
                if (baseDate != null) {
                    result.setBaseDate(baseDate);
                    logger.info("分析模式：推导基准日期 {}", baseDate);
                }
            }
            
            if (result.getTimeGranularity() == null) {
                String granularity = deriveTimeGranularity(query);
                if (granularity != null) {
                    result.setTimeGranularity(granularity);
                    logger.info("分析模式：推导时间粒度 {}", granularity);
                }
            }
            return;
        }
        
        Map<String, ReportQueryRequest.FilterCondition> existingFilters = new HashMap<>();
        for (ReportQueryRequest.FilterCondition fc : result.getFilters()) {
            existingFilters.put(fc.getFieldCode(), fc);
        }
        
        if (!existingFilters.containsKey("trade_date")) {
            String[] dateRange = parseDateFromQuery(query);
            if (dateRange != null && dateRange[0] != null) {
                ReportQueryRequest.FilterCondition fc = new ReportQueryRequest.FilterCondition();
                fc.setFieldCode("trade_date");
                fc.setOperator("BETWEEN");
                fc.setValue(dateRange[0]);
                fc.setValue2(dateRange[1]);
                result.getFilters().add(fc);
            }
        }
    }

    /** 从用户输入中解析日期范围 */
    private String[] parseDateFromQuery(String query) {
        java.util.regex.Pattern p1 = java.util.regex.Pattern.compile("(\\d{4})年(\\d{1,2})月");
        java.util.regex.Matcher m1 = p1.matcher(query);
        if (m1.find()) {
            int year = Integer.parseInt(m1.group(1));
            int month = Integer.parseInt(m1.group(2));
            YearMonth ym = YearMonth.of(year, month);
            String start = ym.atDay(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String end = ym.atEndOfMonth().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            return new String[]{start, end};
        }

        java.util.regex.Pattern pMonthOnly = java.util.regex.Pattern.compile("(\\d{1,2})月(?!份)");
        java.util.regex.Matcher mMonthOnly = pMonthOnly.matcher(query);
        if (mMonthOnly.find()) {
            int month = Integer.parseInt(mMonthOnly.group(1));
            int year = LocalDate.now().getYear();
            YearMonth ym = YearMonth.of(year, month);
            String start = ym.atDay(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String end = ym.atEndOfMonth().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            return new String[]{start, end};
        }

        java.util.regex.Pattern p3 = java.util.regex.Pattern.compile("(\\d{4})年(第)?([一二三四])季度");
        java.util.regex.Matcher m3 = p3.matcher(query);
        if (m3.find()) {
            int year = Integer.parseInt(m3.group(1));
            String quarterStr = m3.group(3);
            int quarter = switch (quarterStr) {
                case "一" -> 1;
                case "二" -> 2;
                case "三" -> 3;
                case "四" -> 4;
                default -> 1;
            };
            LocalDate quarterStart = LocalDate.of(year, (quarter - 1) * 3 + 1, 1);
            LocalDate quarterEnd = quarterStart.plusMonths(3).minusDays(1);
            String start = quarterStart.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String end = quarterEnd.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            return new String[]{start, end};
        }

        java.util.regex.Pattern p2 = java.util.regex.Pattern.compile("(\\d{4})年(?![\\d月])");
        java.util.regex.Matcher m2 = p2.matcher(query);
        if (m2.find()) {
            int year = Integer.parseInt(m2.group(1));
            return new String[]{year + "0101", year + "1231"};
        }

        if (query.contains("本月")) {
            YearMonth ym = YearMonth.now();
            String start = ym.atDay(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String end = ym.atEndOfMonth().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            return new String[]{start, end};
        }

        if (query.contains("上月") || query.contains("上个月")) {
            YearMonth ym = YearMonth.now().minusMonths(1);
            String start = ym.atDay(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String end = ym.atEndOfMonth().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            return new String[]{start, end};
        }

        if (query.contains("本周")) {
            LocalDate today = LocalDate.now();
            LocalDate monday = today.with(DayOfWeek.MONDAY);
            LocalDate sunday = monday.plusDays(6);
            String start = monday.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String end = sunday.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            return new String[]{start, end};
        }

        if (query.contains("上周") || query.contains("上一周")) {
            LocalDate today = LocalDate.now();
            LocalDate lastMonday = today.minusWeeks(1).with(DayOfWeek.MONDAY);
            LocalDate lastSunday = lastMonday.plusDays(6);
            String start = lastMonday.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String end = lastSunday.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            return new String[]{start, end};
        }

        if (query.contains("本季度")) {
            LocalDate today = LocalDate.now();
            int quarter = (today.getMonthValue() - 1) / 3 + 1;
            LocalDate quarterStart = LocalDate.of(today.getYear(), (quarter - 1) * 3 + 1, 1);
            LocalDate quarterEnd = quarterStart.plusMonths(3).minusDays(1);
            String start = quarterStart.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String end = quarterEnd.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            return new String[]{start, end};
        }

        if (query.contains("上季度") || query.contains("上一个季度")) {
            LocalDate today = LocalDate.now();
            int quarter = (today.getMonthValue() - 1) / 3 + 1;
            int lastQuarter = quarter == 1 ? 4 : quarter - 1;
            int lastYear = quarter == 1 ? today.getYear() - 1 : today.getYear();
            LocalDate quarterStart = LocalDate.of(lastYear, (lastQuarter - 1) * 3 + 1, 1);
            LocalDate quarterEnd = LocalDate.of(lastYear, lastQuarter * 3, 1).plusMonths(1).minusDays(1);
            String start = quarterStart.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String end = quarterEnd.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            return new String[]{start, end};
        }

        if (query.contains("本年") || query.contains("今年")) {
            LocalDate today = LocalDate.now();
            String start = LocalDate.of(today.getYear(), 1, 1).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String end = LocalDate.of(today.getYear(), 12, 31).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            return new String[]{start, end};
        }

        if (query.contains("去年") || query.contains("上一年")) {
            int lastYear = LocalDate.now().getYear() - 1;
            return new String[]{lastYear + "0101", lastYear + "1231"};
        }

        return null;
    }
    
    private LocalDate deriveBaseDate(String query) {
        String[] dateRange = parseDateFromQuery(query);
        if (dateRange != null && dateRange[1] != null) {
            return LocalDate.parse(dateRange[1], DateTimeFormatter.ofPattern("yyyyMMdd"));
        }
        return LocalDate.now();
    }
    
    private String deriveTimeGranularity(String query) {
        if (query.contains("季度")) {
            return "QUARTER";
        } else if (query.contains("月")) {
            return "MONTH";
        } else if (query.contains("年")) {
            return "YEAR";
        } else if (query.contains("周")) {
            return "WEEK";
        } else if (query.contains("天")) {
            return "DAY";
        }
        return "MONTH";
    }

    /**
     * 增强规则解析结果：识别多维度分组和聚合需求
     */
    private void enhanceRuleResult(String query, ReportParseResult result) {
        List<String> groupBy = new ArrayList<>();
        Map<String, String> aggregations = new HashMap<>();

        Pattern groupPattern = Pattern.compile("按([^，。、；\\s]+?)统计|按([^，。、；\\s]+?)分类|不同([^，。、；\\s]+?)[下的]|各([^，。、；\\s]+?)");
        Matcher groupMatcher = groupPattern.matcher(query);
        while (groupMatcher.find()) {
            for (int i = 1; i <= 4; i++) {
                String groupWord = groupMatcher.group(i);
                if (groupWord != null && !groupWord.isEmpty()) {
                    String fieldCode = aliasDictionary.get(groupWord.toLowerCase());
                    if (fieldCode != null && fieldMetaCache.containsKey(fieldCode)) {
                        if (!groupBy.contains(fieldCode)) {
                            groupBy.add(fieldCode);
                        }
                    }
                }
            }
        }

        Pattern aggPattern = Pattern.compile("统计([^，。、；\\s]+?)金额|([^，。、；\\s]+?)交易金额");
        Matcher aggMatcher = aggPattern.matcher(query);
        while (aggMatcher.find()) {
            for (int i = 1; i <= 2; i++) {
                String aggWord = aggMatcher.group(i);
                if (aggWord != null && !aggWord.isEmpty()) {
                    String fieldCode = aliasDictionary.get(aggWord.toLowerCase());
                    if (fieldCode != null && fieldMetaCache.containsKey(fieldCode)) {
                        ReportFieldMeta meta = fieldMetaCache.get(fieldCode);
                        if ("Y".equals(meta.getIsMetric())) {
                            aggregations.put(fieldCode, "SUM");
                        }
                    }
                }
            }
        }

        if (query.contains("金额") && aggregations.isEmpty()) {
            aggregations.put("counter_amount", "SUM");
            if (!result.getSelectFields().contains("counter_amount")) {
                result.getSelectFields().add("counter_amount");
            }
        }

        if (!groupBy.isEmpty()) {
            result.setGroupBy(groupBy);
            for (String gb : groupBy) {
                if (!result.getSelectFields().contains(gb)) {
                    result.getSelectFields().add(gb);
                }
            }
        }
        if (!aggregations.isEmpty()) {
            result.setAggregations(aggregations);
        }

        if ("YOY".equals(result.getAnalysisType())) {
            Set<String> existingFields = new LinkedHashSet<>(result.getSelectFields());
            boolean hasCounterAmount = existingFields.contains("counter_amount");
            if (hasCounterAmount) {
                existingFields.remove("counter_amount");
                if (!existingFields.contains("yoy_current_counter")) {
                    existingFields.add("yoy_current_counter");
                }
                if (!existingFields.contains("yoy_last_counter")) {
                    existingFields.add("yoy_last_counter");
                }
                if (!existingFields.contains("yoy_rate_counter")) {
                    existingFields.add("yoy_rate_counter");
                }
            }
            result.setSelectFields(new ArrayList<>(existingFields));
        } else if ("MOM".equals(result.getAnalysisType())) {
            Set<String> existingFields = new LinkedHashSet<>(result.getSelectFields());
            boolean hasCounterAmount = existingFields.contains("counter_amount");
            if (hasCounterAmount) {
                existingFields.remove("counter_amount");
                if (!existingFields.contains("mom_current_counter")) {
                    existingFields.add("mom_current_counter");
                }
                if (!existingFields.contains("mom_last_counter")) {
                    existingFields.add("mom_last_counter");
                }
                if (!existingFields.contains("mom_rate_counter")) {
                    existingFields.add("mom_rate_counter");
                }
            }
            result.setSelectFields(new ArrayList<>(existingFields));
        }

        // 根据交易类型修正期限字段：掉期交易应该使用 swap_term，远期交易应该使用 forward_term
        String tradeType = null;
        for (ReportQueryRequest.FilterCondition fc : result.getFilters()) {
            if ("trade_type".equals(fc.getFieldCode())) {
                tradeType = fc.getValue();
                break;
            }
        }
        if (tradeType != null) {
            if ("SWAP".equals(tradeType)) {
                // 掉期交易：将 forward_term 替换为 swap_term
                List<String> newSelectFields = new ArrayList<>();
                for (String field : result.getSelectFields()) {
                    if ("forward_term".equals(field)) {
                        newSelectFields.add("swap_term");
                    } else {
                        newSelectFields.add(field);
                    }
                }
                result.setSelectFields(newSelectFields);
                List<String> newGroupBy = new ArrayList<>();
                for (String field : result.getGroupBy()) {
                    if ("forward_term".equals(field)) {
                        newGroupBy.add("swap_term");
                    } else {
                        newGroupBy.add(field);
                    }
                }
                result.setGroupBy(newGroupBy);
            } else if ("FORWARD".equals(tradeType)) {
                // 远期交易：将 swap_term 替换为 forward_term
                List<String> newSelectFields = new ArrayList<>();
                for (String field : result.getSelectFields()) {
                    if ("swap_term".equals(field)) {
                        newSelectFields.add("forward_term");
                    } else {
                        newSelectFields.add(field);
                    }
                }
                result.setSelectFields(newSelectFields);
                List<String> newGroupBy = new ArrayList<>();
                for (String field : result.getGroupBy()) {
                    if ("swap_term".equals(field)) {
                        newGroupBy.add("forward_term");
                    } else {
                        newGroupBy.add(field);
                    }
                }
                result.setGroupBy(newGroupBy);
            }
        }

        // 根据分析类型修正分析计算字段：环比分析应该使用 mom_* 字段，同比分析应该使用 yoy_* 字段
        String analysisType = result.getAnalysisType();
        if (analysisType != null) {
            if ("MOM".equals(analysisType)) {
                // 环比分析：将 yoy_* 字段替换为 mom_* 字段
                List<String> newSelectFields = new ArrayList<>();
                for (String field : result.getSelectFields()) {
                    if (field.startsWith("yoy_")) {
                        newSelectFields.add(field.replaceFirst("^yoy_", "mom_"));
                    } else {
                        newSelectFields.add(field);
                    }
                }
                result.setSelectFields(newSelectFields);
            } else if ("YOY".equals(analysisType)) {
                // 同比分析：将 mom_* 字段替换为 yoy_* 字段
                List<String> newSelectFields = new ArrayList<>();
                for (String field : result.getSelectFields()) {
                    if (field.startsWith("mom_")) {
                        newSelectFields.add(field.replaceFirst("^mom_", "yoy_"));
                    } else {
                        newSelectFields.add(field);
                    }
                }
                result.setSelectFields(newSelectFields);
            }

            // 设置分析时间粒度（基准日期由前端从日期范围中提取）
            if (analysisType != null) {
                String granularity = detectTimeGranularity(query);
                if (granularity == null) {
                    granularity = "MONTH";
                }
                result.setTimeGranularity(granularity);
            }
        }
    }
}
