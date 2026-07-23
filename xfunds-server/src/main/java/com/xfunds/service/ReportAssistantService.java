package com.xfunds.service;

import com.xfunds.dto.FieldMetaVO;
import com.xfunds.dto.ReportParseResult;
import com.xfunds.dto.ReportQueryRequest;
import com.xfunds.dto.ReportQueryResult;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;
import java.util.Map;

/**
 * 报表助手服务
 */
public interface ReportAssistantService {

    /** 获取所有启用字段（按分组返回） */
    Map<String, List<FieldMetaVO>> listFields();

    /** 自然语言解析为字段列表+过滤条件 */
    ReportParseResult parseNaturalLanguage(String query);

    /** 执行报表查询 */
    ReportQueryResult queryReport(ReportQueryRequest request);

    /** 导出 Excel（直接写入 HttpServletResponse） */
    void exportReport(ReportQueryRequest request, HttpServletResponse response);

    /** 获取字段码值字典（用于过滤条件值选择） */
    Map<String, List<OptionValue>> listFieldDict();

    /** 码值选项 */
    class OptionValue {
        public String value;
        public String label;
        public OptionValue(String value, String label) {
            this.value = value;
            this.label = label;
        }
    }
}
