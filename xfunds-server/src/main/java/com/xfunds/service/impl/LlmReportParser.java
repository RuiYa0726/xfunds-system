package com.xfunds.service.impl;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.xfunds.config.LlmConfig;
import com.xfunds.dto.ReportParseResult;
import com.xfunds.dto.ReportQueryRequest;
import com.xfunds.entity.ReportFieldMeta;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class LlmReportParser {

    private static final Logger logger = LoggerFactory.getLogger(LlmReportParser.class);

    @Autowired
    private LlmConfig llmConfig;

    private Map<String, ReportFieldMeta> fieldMetaCache = new HashMap<>();

    private List<ReportTemplate> reportTemplates = new ArrayList<>();

    public static class ReportTemplate {
        private String id;
        private String name;
        private List<String> keywords;
        private List<String> selectFields;
        private List<String> groupBy;
        private Map<String, String> aggregations;
        private List<Map<String, String>> filters;
        private List<Map<String, String>> orderBy;
        private String summary;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public List<String> getKeywords() { return keywords; }
        public void setKeywords(List<String> keywords) { this.keywords = keywords; }
        public List<String> getSelectFields() { return selectFields; }
        public void setSelectFields(List<String> selectFields) { this.selectFields = selectFields; }
        public List<String> getGroupBy() { return groupBy; }
        public void setGroupBy(List<String> groupBy) { this.groupBy = groupBy; }
        public Map<String, String> getAggregations() { return aggregations; }
        public void setAggregations(Map<String, String> aggregations) { this.aggregations = aggregations; }
        public List<Map<String, String>> getFilters() { return filters; }
        public void setFilters(List<Map<String, String>> filters) { this.filters = filters; }
        public List<Map<String, String>> getOrderBy() { return orderBy; }
        public void setOrderBy(List<Map<String, String>> orderBy) { this.orderBy = orderBy; }
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
    }

    @PostConstruct
    public void init() {
        try {
            ClassPathResource resource = new ClassPathResource("report_templates.json");
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    String content = IoUtil.readUtf8(is);
                    JSONArray arr = JSONUtil.parseArray(content);
                    for (Object o : arr) {
                        JSONObject obj = (JSONObject) o;
                        ReportTemplate template = new ReportTemplate();
                        template.setId(obj.getStr("id"));
                        template.setName(obj.getStr("name"));
                        template.setKeywords(obj.getJSONArray("keywords").toList(String.class));
                        template.setSelectFields(obj.getJSONArray("selectFields").toList(String.class));
                        template.setGroupBy(obj.getJSONArray("groupBy").toList(String.class));
                        template.setAggregations((Map<String, String>) (Map<?, ?>) new HashMap<>(obj.getJSONObject("aggregations")));
                        template.setFilters((List<Map<String, String>>) (List<?>) new ArrayList<>(obj.getJSONArray("filters")));
                        template.setOrderBy((List<Map<String, String>>) (List<?>) new ArrayList<>(obj.getJSONArray("orderBy")));
                        template.setSummary(obj.getStr("summary"));
                        reportTemplates.add(template);
                    }
                    logger.info("报表模板加载完成：{} 个", reportTemplates.size());
                }
            }
        } catch (Exception e) {
            logger.warn("加载报表模板失败：{}", e.getMessage());
        }
    }

    public void setFieldMetaCache(Map<String, ReportFieldMeta> cache) {
        this.fieldMetaCache = cache;
    }

    public ReportParseResult tryMatchTemplate(String query) {
        if (reportTemplates.isEmpty()) {
            return null;
        }
        
        String lowerQuery = query.toLowerCase();
        int maxScore = 0;
        ReportTemplate matched = null;
        
        for (ReportTemplate template : reportTemplates) {
            int score = 0;
            for (String keyword : template.getKeywords()) {
                if (lowerQuery.contains(keyword.toLowerCase())) {
                    score += 2;
                }
            }
            if (score > maxScore) {
                maxScore = score;
                matched = template;
            }
        }
        
        if (matched != null && maxScore >= 2) {
            logger.info("匹配到报表模板：{}", matched.getName());
            ReportParseResult result = new ReportParseResult();
            result.setSelectFields(new ArrayList<>(matched.getSelectFields()));
            result.setGroupBy(new ArrayList<>(matched.getGroupBy()));
            result.setAggregations(new HashMap<>(matched.getAggregations()));
            
            List<ReportQueryRequest.FilterCondition> filters = new ArrayList<>();
            for (Map<String, String> f : matched.getFilters()) {
                ReportQueryRequest.FilterCondition fc = new ReportQueryRequest.FilterCondition();
                fc.setFieldCode(f.get("fieldCode"));
                fc.setOperator(f.get("operator"));
                fc.setValue(f.get("value"));
                filters.add(fc);
            }
            result.setFilters(filters);
            
            List<ReportQueryRequest.OrderByItem> orderBy = new ArrayList<>();
            for (Map<String, String> o : matched.getOrderBy()) {
                ReportQueryRequest.OrderByItem item = new ReportQueryRequest.OrderByItem();
                item.setFieldCode(o.get("fieldCode"));
                item.setDirection(o.get("direction"));
                orderBy.add(item);
            }
            result.setOrderBy(orderBy);
            
            result.setSummary(matched.getSummary());
            result.setSuccess(true);
            result.setParseSource("TEMPLATE");
            return result;
        }
        return null;
    }

    public ReportParseResult parse(String query) throws Exception {
        if (!llmConfig.isEnabled()) {
            throw new IllegalStateException("LLM 解析未启用");
        }
        if (StrUtil.isBlank(llmConfig.getApiKey()) || "YOUR_ZHIPU_API_KEY_HERE".equals(llmConfig.getApiKey())) {
            throw new IllegalStateException("LLM API Key 未配置");
        }

        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(query);

        String url = llmConfig.getBaseUrl() + "/chat/completions";
        JSONObject requestBody = new JSONObject();
        requestBody.set("model", llmConfig.getModel());
        requestBody.set("temperature", llmConfig.getTemperature());
        requestBody.set("response_format", new JSONObject().set("type", "json_object"));
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> sysMsg = new HashMap<>();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);
        messages.add(sysMsg);
        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt);
        messages.add(userMsg);
        requestBody.set("messages", messages);

        logger.info("调用 LLM 解析：query={}", query);
        long start = System.currentTimeMillis();
        String respStr = HttpRequest.post(url)
                .header("Authorization", "Bearer " + llmConfig.getApiKey())
                .header("Content-Type", "application/json")
                .body(requestBody.toString())
                .timeout(llmConfig.getTimeout())
                .execute()
                .body();
        long cost = System.currentTimeMillis() - start;
        logger.info("LLM 响应耗时：{}ms", cost);

        JSONObject resp = JSONUtil.parseObj(respStr);
        if (resp.containsKey("error")) {
            throw new RuntimeException("LLM API 错误：" + resp.getStr("error"));
        }
        JSONArray choices = resp.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("LLM 返回为空");
        }
        String content = choices.getJSONObject(0).getJSONObject("message").getStr("content");
        logger.info("LLM 返回内容：{}", content);

        ReportParseResult result = parseLlmResponse(content);
        
        if (!result.isSuccess()) {
            throw new RuntimeException("LLM 解析失败：" + result.getSummary());
        }
        
        // 检测分析类型：优先从用户输入中检测"同比"或"环比"关键词
        detectAnalysisTypeFromQuery(query, result);
        
        enhanceResultWithDate(query, result);
        
        return result;
    }

    /** 从用户输入中检测分析类型 */
    private void detectAnalysisTypeFromQuery(String query, ReportParseResult result) {
        if (query.contains("同比")) {
            result.setAnalysisType("YOY");
            logger.info("检测到同比关键词，设置分析类型为YOY");
        } else if (query.contains("环比")) {
            result.setAnalysisType("MOM");
            logger.info("检测到环比关键词，设置分析类型为MOM");
        } else {
            if (result.getAnalysisType() != null) {
                logger.info("用户输入不包含同比/环比关键词，清除LLM错误设置的分析类型 {}", result.getAnalysisType());
                result.setAnalysisType(null);
                result.setTimeGranularity(null);
                result.setBaseDate(null);
            }
        }
    }

    private String buildSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        
        sb.append("你是一个专业的外汇交易报表语义解析器。请仔细分析用户的自然语言需求，提取以下信息：\n");
        sb.append("1. 意图：用户想查询什么数据（如：统计交易金额、查看明细等）\n");
        sb.append("2. 交易类型：即期(SPOT)/远期(FORWARD)/掉期(SWAP)/期权(OPTION)\n");
        sb.append("3. 维度字段：用于分组的字段（如用途编码、客户类型、期限等）\n");
        sb.append("4. 指标字段：用于聚合计算的字段（如名义金额、期权费等）\n");
        sb.append("5. 过滤条件：时间范围、交易类型、币种等\n");
        sb.append("6. 排序要求：按哪个字段排序，升序还是降序\n");
        sb.append("\n");

        sb.append("## 字段白名单（必须从以下列表中选择）\n");
        sb.append("| fieldCode | 显示名 | 类型 | 是否维度 | 是否指标 | 默认聚合 |\n");
        sb.append("|---|---|---|---|---|---|\n");
        for (ReportFieldMeta m : fieldMetaCache.values()) {
            sb.append(String.format("| %s | %s | %s | %s | %s | %s |\n",
                    m.getFieldCode(),
                    m.getDisplayNameCn(),
                    m.getDataType(),
                    m.getIsDimension(),
                    m.getIsMetric(),
                    m.getDefaultAgg() == null ? "" : m.getDefaultAgg()));
        }

        sb.append("\n## 字段映射参考（帮助理解业务术语）\n");
        sb.append("- 用途编码 -> purpose_code\n");
        sb.append("- 客户类型 -> cust_customer_type\n");
        sb.append("- 企业类型 -> cust_corp_type\n");
        sb.append("- 交易金额/成交金额/金额 -> counter_amount（指标，默认SUM，人民币）\n");
        sb.append("- 名义金额/本金 -> notional_amount（指标，默认SUM，外币）\n");
        sb.append("- 客户名称 -> customer_name\n");
        sb.append("- 客户ID -> customer_id\n");
        sb.append("- 交易日期/交易日 -> trade_date\n");
        sb.append("- 交易类型 -> trade_type\n");
        sb.append("- 期限分组 -> tenor_bucket（掉期/远期报表使用）\n");
        sb.append("- 掉期类型 -> swap_type\n");
        sb.append("- 期权类型 -> option_type\n");
        sb.append("- 分行编码 -> branch_code\n");
        sb.append("- 机构名称 -> org_name\n");

        sb.append("\n## 解析规则\n");
        sb.append("1. **selectFields**：必须包含所有分组字段和聚合字段\n");
        sb.append("2. **groupBy**：所有维度字段必须出现在此处。识别模式：\"按X统计\"、\"按X1、X2分类\"、\"不同X\"、\"各X\"中的X为分组字段\n");
        sb.append("3. **aggregations**：指标字段的聚合函数，key=fieldCode，value=SUM/AVG/COUNT/MAX/MIN。默认SUM\n");
        sb.append("4. **filters**：过滤条件列表\n");
        sb.append("   - 交易类型映射：即期=SPOT、远期=FORWARD、掉期=SWAP、期权=OPTION\n");
        sb.append("   - 时间表达：\"2026年6月\"=当月1日至月末、\"本月\"=当前月份、\"上月\"=上个月、\"一个月内\"=最近30天\n");
        sb.append("   - 日期格式：yyyyMMdd\n");
        sb.append("5. **orderBy**：排序条件，默认按分组字段升序\n");
        sb.append("6. **分析模式（同比/环比）**：当用户输入包含\"同比\"或\"环比\"关键词时，必须输出analysisType和timeGranularity字段\n");
        sb.append("   - analysisType：同比=YOY、环比=MOM\n");
        sb.append("   - timeGranularity：时间粒度，可选DAY/WEEK/MONTH/QUARTER/YEAR，默认MONTH\n");
        sb.append("   - baseDate：分析基准日期，格式yyyy-MM-dd，使用日期范围的结束日期\n");
        sb.append("   - 同比：本期与去年同期对比（如2026年6月对比2025年6月）\n");
        sb.append("   - 环比：本期与上期对比（如2026年6月对比2026年5月、2026年一季度对比2025年四季度）\n");
        sb.append("7. **查询类型判断**：\n");
        sb.append("   - 明细查询：用户明确要求查看具体交易数据、明细、列表等（如\"查看交易数据\"、\"查询明细\"），此时groupBy应为空数组，aggregations应为空对象\n");
        sb.append("   - 聚合查询：用户要求统计、汇总、排名等（如\"统计金额\"、\"汇总数据\"、\"排名\"），此时需要设置groupBy和aggregations\n");
        sb.append("   - 如果用户指定了具体的交易编号、客户ID等唯一标识，通常是明细查询\n");

        sb.append("\n## 详细示例\n");
        
        sb.append("\n### 示例1：即期月报表（按用途编码+客户类型统计）\n");
        sb.append("用户输入：生成即期2026年6月月报表，按用途编码统计不同类型客户在一个月内的交易金额\n");
        sb.append("解析结果：\n");
        sb.append("{\n");
        sb.append("  \"selectFields\": [\"purpose_code\", \"cust_customer_type\", \"counter_amount\"],\n");
        sb.append("  \"groupBy\": [\"purpose_code\", \"cust_customer_type\"],\n");
        sb.append("  \"aggregations\": {\"counter_amount\": \"SUM\"},\n");
        sb.append("  \"filters\": [\n");
        sb.append("    {\"fieldCode\": \"trade_type\", \"operator\": \"EQ\", \"value\": \"SPOT\"},\n");
        sb.append("    {\"fieldCode\": \"trade_date\", \"operator\": \"BETWEEN\", \"value\": \"20260601\", \"value2\": \"20260630\"}\n");
        sb.append("  ],\n");
        sb.append("  \"orderBy\": [{\"fieldCode\": \"purpose_code\", \"direction\": \"ASC\"}, {\"fieldCode\": \"cust_customer_type\", \"direction\": \"ASC\"}],\n");
        sb.append("  \"summary\": \"按用途编码和客户类型分组统计2026年6月即期交易金额\"\n");
        sb.append("}\n");

        sb.append("\n### 示例2：掉期统计报表（按期限统计）\n");
        sb.append("用户输入：生成掉期统计报表，统计2026年6月不同期限下的掉期交易金额\n");
        sb.append("解析结果：\n");
        sb.append("{\n");
        sb.append("  \"selectFields\": [\"tenor_bucket\", \"counter_amount\"],\n");
        sb.append("  \"groupBy\": [\"tenor_bucket\"],\n");
        sb.append("  \"aggregations\": {\"counter_amount\": \"SUM\"},\n");
        sb.append("  \"filters\": [\n");
        sb.append("    {\"fieldCode\": \"trade_type\", \"operator\": \"EQ\", \"value\": \"SWAP\"},\n");
        sb.append("    {\"fieldCode\": \"trade_date\", \"operator\": \"BETWEEN\", \"value\": \"20260601\", \"value2\": \"20260630\"}\n");
        sb.append("  ],\n");
        sb.append("  \"orderBy\": [{\"fieldCode\": \"tenor_bucket\", \"direction\": \"ASC\"}],\n");
        sb.append("  \"summary\": \"按期限分组统计2026年6月掉期交易金额\"\n");
        sb.append("}\n");

        sb.append("\n### 示例3：按分行统计交易金额\n");
        sb.append("用户输入：各分行本月交易金额排名\n");
        sb.append("解析结果：\n");
        sb.append("{\n");
        sb.append("  \"selectFields\": [\"branch_code\", \"org_name\", \"counter_amount\"],\n");
        sb.append("  \"groupBy\": [\"branch_code\", \"org_name\"],\n");
        sb.append("  \"aggregations\": {\"counter_amount\": \"SUM\"},\n");
        sb.append("  \"filters\": [\n");
        sb.append("    {\"fieldCode\": \"trade_date\", \"operator\": \"BETWEEN\", \"value\": \"20260701\", \"value2\": \"20260731\"}\n");
        sb.append("  ],\n");
        sb.append("  \"orderBy\": [{\"fieldCode\": \"counter_amount\", \"direction\": \"DESC\"}],\n");
        sb.append("  \"summary\": \"按分行分组统计本月交易金额，按金额降序排名\"\n");
        sb.append("}\n");

        sb.append("\n### 示例4：客户交易汇总\n");
        sb.append("用户输入：统计大客户的交易金额排名\n");
        sb.append("解析结果：\n");
        sb.append("{\n");
        sb.append("  \"selectFields\": [\"customer_id\", \"customer_name\", \"cust_customer_type\", \"counter_amount\"],\n");
        sb.append("  \"groupBy\": [\"customer_id\", \"customer_name\", \"cust_customer_type\"],\n");
        sb.append("  \"aggregations\": {\"counter_amount\": \"SUM\"},\n");
        sb.append("  \"filters\": [],\n");
        sb.append("  \"orderBy\": [{\"fieldCode\": \"counter_amount\", \"direction\": \"DESC\"}],\n");
        sb.append("  \"summary\": \"按客户分组统计交易金额（降序排名）\"\n");
        sb.append("}\n");

        sb.append("\n### 示例5：同比分析（月度）\n");
        sb.append("用户输入：从分行维度查看2026年6月交易金额同比数据\n");
        sb.append("解析结果：\n");
        sb.append("{\n");
        sb.append("  \"selectFields\": [\"branch_code\", \"counter_amount\"],\n");
        sb.append("  \"groupBy\": [\"branch_code\"],\n");
        sb.append("  \"aggregations\": {\"counter_amount\": \"SUM\"},\n");
        sb.append("  \"filters\": [\n");
        sb.append("    {\"fieldCode\": \"trade_date\", \"operator\": \"BETWEEN\", \"value\": \"20260601\", \"value2\": \"20260630\"}\n");
        sb.append("  ],\n");
        sb.append("  \"orderBy\": [{\"fieldCode\": \"branch_code\", \"direction\": \"ASC\"}],\n");
        sb.append("  \"analysisType\": \"YOY\",\n");
        sb.append("  \"timeGranularity\": \"MONTH\",\n");
        sb.append("  \"baseDate\": \"2026-06-30\",\n");
        sb.append("  \"summary\": \"按分行分组统计2026年6月交易金额同比数据（本期与2025年6月对比）\"\n");
        sb.append("}\n");

        sb.append("\n### 示例6：环比分析（季度）\n");
        sb.append("用户输入：从交易类型维度查看2026年一季度交易金额环比数据\n");
        sb.append("解析结果：\n");
        sb.append("{\n");
        sb.append("  \"selectFields\": [\"trade_type\", \"counter_amount\"],\n");
        sb.append("  \"groupBy\": [\"trade_type\"],\n");
        sb.append("  \"aggregations\": {\"counter_amount\": \"SUM\"},\n");
        sb.append("  \"filters\": [\n");
        sb.append("    {\"fieldCode\": \"trade_date\", \"operator\": \"BETWEEN\", \"value\": \"20260101\", \"value2\": \"20260331\"}\n");
        sb.append("  ],\n");
        sb.append("  \"orderBy\": [{\"fieldCode\": \"trade_type\", \"direction\": \"ASC\"}],\n");
        sb.append("  \"analysisType\": \"MOM\",\n");
        sb.append("  \"timeGranularity\": \"QUARTER\",\n");
        sb.append("  \"baseDate\": \"2026-03-31\",\n");
        sb.append("  \"summary\": \"按交易类型分组统计2026年一季度交易金额环比数据（本期与2025年四季度对比）\"\n");
        sb.append("}\n");

        sb.append("\n### 示例7：同比分析（年度）\n");
        sb.append("用户输入：各分行2026年交易金额同比增长率\n");
        sb.append("解析结果：\n");
        sb.append("{\n");
        sb.append("  \"selectFields\": [\"branch_code\", \"org_name\", \"counter_amount\"],\n");
        sb.append("  \"groupBy\": [\"branch_code\", \"org_name\"],\n");
        sb.append("  \"aggregations\": {\"counter_amount\": \"SUM\"},\n");
        sb.append("  \"filters\": [\n");
        sb.append("    {\"fieldCode\": \"trade_date\", \"operator\": \"BETWEEN\", \"value\": \"20260101\", \"value2\": \"20261231\"}\n");
        sb.append("  ],\n");
        sb.append("  \"orderBy\": [{\"fieldCode\": \"counter_amount\", \"direction\": \"DESC\"}],\n");
        sb.append("  \"analysisType\": \"YOY\",\n");
        sb.append("  \"timeGranularity\": \"YEAR\",\n");
        sb.append("  \"baseDate\": \"2026-12-31\",\n");
        sb.append("  \"summary\": \"按分行分组统计2026年交易金额同比增长率（本期与2025年对比）\"\n");
        sb.append("}\n");

        sb.append("\n### 示例8：客户明细查询\n");
        sb.append("用户输入：查看客户C20240003在2026年6月的交易数据，交易编号、交易类型、交易日、交易金额、交易到期日\n");
        sb.append("解析结果：\n");
        sb.append("{\n");
        sb.append("  \"selectFields\": [\"trade_id\", \"trade_type\", \"trade_date\", \"counter_amount\", \"maturity_date\"],\n");
        sb.append("  \"groupBy\": [],\n");
        sb.append("  \"aggregations\": {},\n");
        sb.append("  \"filters\": [\n");
        sb.append("    {\"fieldCode\": \"customer_id\", \"operator\": \"EQ\", \"value\": \"C20240003\"},\n");
        sb.append("    {\"fieldCode\": \"trade_date\", \"operator\": \"BETWEEN\", \"value\": \"20260601\", \"value2\": \"20260630\"}\n");
        sb.append("  ],\n");
        sb.append("  \"orderBy\": [{\"fieldCode\": \"trade_date\", \"direction\": \"ASC\"}],\n");
        sb.append("  \"summary\": \"查询客户C20240003在2026年6月的交易明细数据\"\n");
        sb.append("}\n");

        sb.append("\n## 输出格式（严格 JSON，不要 markdown 代码块）\n");
        sb.append("{\n");
        sb.append("  \"selectFields\": [\"fieldCode1\", \"fieldCode2\", ...],\n");
        sb.append("  \"groupBy\": [\"fieldCode1\", \"fieldCode2\", ...],\n");
        sb.append("  \"aggregations\": {\"fieldCode\": \"SUM\"},\n");
        sb.append("  \"filters\": [\n");
        sb.append("    {\"fieldCode\": \"trade_type\", \"operator\": \"EQ\", \"value\": \"SPOT\"},\n");
        sb.append("    {\"fieldCode\": \"trade_date\", \"operator\": \"BETWEEN\", \"value\": \"20260601\", \"value2\": \"20260630\"}\n");
        sb.append("  ],\n");
        sb.append("  \"orderBy\": [{\"fieldCode\": \"fieldCode1\", \"direction\": \"ASC\"}],\n");
        sb.append("  \"analysisType\": \"YOY/MOM/null\",\n");
        sb.append("  \"timeGranularity\": \"DAY/WEEK/MONTH/QUARTER/YEAR\",\n");
        sb.append("  \"baseDate\": \"yyyy-MM-dd\",\n");
        sb.append("  \"summary\": \"简短中文摘要，描述解析结果\"\n");
        sb.append("}\n");

        sb.append("\n## 注意事项\n");
        sb.append("- fieldCode 必须来自白名单，不得编造\n");
        sb.append("- 指标字段必须设置聚合函数，默认SUM\n");
        sb.append("- 所有分组字段必须同时出现在selectFields和groupBy中\n");
        sb.append("- 多维度分组时，groupBy需包含所有维度字段\n");
        sb.append("- 日期范围必须使用BETWEEN操作符，格式为yyyyMMdd\n");
        sb.append("- 当输入包含\"同比\"或\"环比\"时，必须设置analysisType、timeGranularity和baseDate字段\n");
        sb.append("- timeGranularity根据日期表达自动推断：\"6月\"=MONTH、\"一季度\"=QUARTER、\"2026年\"=YEAR、\"今天\"=DAY\n");
        sb.append("- 如果无法识别，返回 {\"summary\": \"无法解析\", \"success\": false}\n");

        return sb.toString();
    }

    private String buildUserPrompt(String query) {
        LocalDate today = LocalDate.now();
        StringBuilder sb = new StringBuilder();
        sb.append("当前日期：").append(today.format(DateTimeFormatter.ISO_DATE)).append("\n");
        sb.append("用户输入：").append(query).append("\n");
        sb.append("\n请按照上述规则解析，并输出严格的JSON格式。");
        return sb.toString();
    }

    private ReportParseResult parseLlmResponse(String content) {
        String json = content.trim();
        if (json.startsWith("```")) {
            json = json.replaceAll("^```(json)?", "").replaceAll("```$", "").trim();
        }

        JSONObject obj = JSONUtil.parseObj(json);
        ReportParseResult result = new ReportParseResult();

        JSONArray selectArr = obj.getJSONArray("selectFields");
        if (selectArr != null) {
            List<String> selectFields = new ArrayList<>();
            for (Object o : selectArr) {
                String code = String.valueOf(o);
                if (fieldMetaCache.containsKey(code)) {
                    selectFields.add(code);
                }
            }
            result.setSelectFields(selectFields);
        }

        JSONArray groupArr = obj.getJSONArray("groupBy");
        if (groupArr != null) {
            List<String> groupBy = new ArrayList<>();
            for (Object o : groupArr) {
                String code = String.valueOf(o);
                if (fieldMetaCache.containsKey(code)) {
                    groupBy.add(code);
                }
            }
            result.setGroupBy(groupBy);
        }

        JSONArray filterArr = obj.getJSONArray("filters");
        if (filterArr != null) {
            List<ReportQueryRequest.FilterCondition> filters = new ArrayList<>();
            for (Object o : filterArr) {
                JSONObject f = (JSONObject) o;
                String fieldCode = f.getStr("fieldCode");
                if (!fieldMetaCache.containsKey(fieldCode)) {
                    logger.warn("LLM 返回的过滤字段不在白名单：{}", fieldCode);
                    continue;
                }
                ReportQueryRequest.FilterCondition fc = new ReportQueryRequest.FilterCondition();
                fc.setFieldCode(fieldCode);
                fc.setOperator(f.getStr("operator"));
                fc.setValue(f.getStr("value"));
                fc.setValue2(f.getStr("value2"));
                JSONArray values = f.getJSONArray("values");
                if (values != null) {
                    fc.setValues(values.toList(String.class));
                }
                filters.add(fc);
            }
            result.setFilters(filters);
        }

        JSONObject aggregations = obj.getJSONObject("aggregations");
        if (aggregations != null) {
            Map<String, String> aggMap = new HashMap<>();
            for (String key : aggregations.keySet()) {
                if (fieldMetaCache.containsKey(key)) {
                    aggMap.put(key, aggregations.getStr(key));
                }
            }
            result.setAggregations(aggMap);
        }

        JSONArray orderArr = obj.getJSONArray("orderBy");
        if (orderArr != null) {
            List<ReportQueryRequest.OrderByItem> orderBy = new ArrayList<>();
            for (Object o : orderArr) {
                JSONObject ord = (JSONObject) o;
                String fieldCode = ord.getStr("fieldCode");
                if (!fieldMetaCache.containsKey(fieldCode)) {
                    logger.warn("LLM 返回的排序字段不在白名单：{}", fieldCode);
                    continue;
                }
                ReportQueryRequest.OrderByItem item = new ReportQueryRequest.OrderByItem();
                item.setFieldCode(fieldCode);
                item.setDirection(ord.getStr("direction"));
                orderBy.add(item);
            }
            result.setOrderBy(orderBy);
        }

        result.setSummary(obj.getStr("summary", ""));
        
        result.setAnalysisType(obj.getStr("analysisType"));
        result.setTimeGranularity(obj.getStr("timeGranularity"));
        
        String baseDateStr = obj.getStr("baseDate");
        if (baseDateStr != null && !baseDateStr.isEmpty()) {
            try {
                result.setBaseDate(LocalDate.parse(baseDateStr, DateTimeFormatter.ISO_DATE));
            } catch (Exception e) {
                logger.warn("解析 baseDate 失败：{}", baseDateStr);
            }
        }
        
        boolean success = obj.getBool("success", true);
        if (!success) {
            result.setSuccess(false);
        } else {
            result.setSuccess(!result.getSelectFields().isEmpty());
        }
        result.setParseSource("LLM");
        
        return result;
    }

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

        java.util.regex.Pattern pQuarter = java.util.regex.Pattern.compile("(\\d{4})年(第)?([一二三四])季度");
        java.util.regex.Matcher mQuarter = pQuarter.matcher(query);
        if (mQuarter.find()) {
            int year = Integer.parseInt(mQuarter.group(1));
            String quarterStr = mQuarter.group(3);
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
            LocalDate now = LocalDate.now();
            LocalDate start = now.with(java.time.DayOfWeek.MONDAY);
            LocalDate end = now.with(java.time.DayOfWeek.SUNDAY);
            return new String[]{start.format(DateTimeFormatter.ofPattern("yyyyMMdd")), 
                               end.format(DateTimeFormatter.ofPattern("yyyyMMdd"))};
        }

        if (query.contains("上周") || query.contains("上个星期")) {
            LocalDate now = LocalDate.now();
            LocalDate start = now.with(java.time.DayOfWeek.MONDAY).minusWeeks(1);
            LocalDate end = now.with(java.time.DayOfWeek.SUNDAY).minusWeeks(1);
            return new String[]{start.format(DateTimeFormatter.ofPattern("yyyyMMdd")), 
                               end.format(DateTimeFormatter.ofPattern("yyyyMMdd"))};
        }

        if (query.contains("本季度")) {
            LocalDate now = LocalDate.now();
            int quarter = (now.getMonthValue() - 1) / 3 + 1;
            LocalDate start = LocalDate.of(now.getYear(), (quarter - 1) * 3 + 1, 1);
            LocalDate end = start.plusMonths(3).minusDays(1);
            return new String[]{start.format(DateTimeFormatter.ofPattern("yyyyMMdd")), 
                               end.format(DateTimeFormatter.ofPattern("yyyyMMdd"))};
        }

        if (query.contains("上季度") || query.contains("上个季度")) {
            LocalDate now = LocalDate.now();
            int quarter = (now.getMonthValue() - 1) / 3 + 1;
            LocalDate start = LocalDate.of(now.getYear(), (quarter - 1) * 3 + 1, 1).minusMonths(3);
            LocalDate end = start.plusMonths(3).minusDays(1);
            return new String[]{start.format(DateTimeFormatter.ofPattern("yyyyMMdd")), 
                               end.format(DateTimeFormatter.ofPattern("yyyyMMdd"))};
        }

        if (query.contains("本年") || query.contains("今年")) {
            LocalDate now = LocalDate.now();
            return new String[]{now.getYear() + "0101", now.getYear() + "1231"};
        }

        if (query.contains("去年") || query.contains("上年")) {
            int lastYear = LocalDate.now().getYear() - 1;
            return new String[]{lastYear + "0101", lastYear + "1231"};
        }

        java.util.regex.Pattern p3 = java.util.regex.Pattern.compile("最近?(\\d+)天");
        java.util.regex.Matcher m3 = p3.matcher(query);
        if (m3.find()) {
            int days = Integer.parseInt(m3.group(1));
            LocalDate end = LocalDate.now();
            LocalDate start = end.minusDays(days);
            return new String[]{start.format(DateTimeFormatter.ofPattern("yyyyMMdd")), 
                               end.format(DateTimeFormatter.ofPattern("yyyyMMdd"))};
        }

        return null;
    }
}
