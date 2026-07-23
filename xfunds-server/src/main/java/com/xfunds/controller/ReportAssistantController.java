package com.xfunds.controller;

import com.xfunds.common.Result;
import com.xfunds.dto.FieldMetaVO;
import com.xfunds.dto.ReportParseResult;
import com.xfunds.dto.ReportQueryRequest;
import com.xfunds.dto.ReportQueryResult;
import com.xfunds.service.ReportAssistantService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 报表助手控制器
 */
@RestController
@RequestMapping("/api/report")
public class ReportAssistantController {

    @Autowired
    private ReportAssistantService reportAssistantService;

    /**
     * 获取字段元数据（按分组返回，前端字段面板用）
     */
    @GetMapping("/fields")
    public Result<Map<String, List<FieldMetaVO>>> listFields() {
        return Result.ok(reportAssistantService.listFields());
    }

    /**
     * 获取字段码值字典（用于过滤条件值选择）
     */
    @GetMapping("/dict")
    public Result<Map<String, List<ReportAssistantService.OptionValue>>> listDict() {
        return Result.ok(reportAssistantService.listFieldDict());
    }

    /**
     * 自然语言解析为字段列表 + 过滤条件
     */
    @PostMapping("/parse")
    public Result<ReportParseResult> parse(@RequestBody ParseRequest req) {
        return Result.ok(reportAssistantService.parseNaturalLanguage(req.getQuery()));
    }

    /**
     * 执行报表查询
     */
    @PostMapping("/query")
    public Result<ReportQueryResult> query(@RequestBody ReportQueryRequest request) {
        try {
            return Result.ok(reportAssistantService.queryReport(request));
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        } catch (Exception e) {
            return Result.fail(500, "查询失败：" + e.getMessage());
        }
    }

    /**
     * 导出 Excel
     */
    @PostMapping("/export")
    public void export(@RequestBody ReportQueryRequest request, HttpServletResponse response) {
        reportAssistantService.exportReport(request, response);
    }

    /** 解析请求体 */
    public static class ParseRequest {
        private String query;
        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }
    }
}
