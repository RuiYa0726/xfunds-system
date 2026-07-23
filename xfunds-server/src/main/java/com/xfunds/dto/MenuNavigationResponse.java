package com.xfunds.dto;

import lombok.Data;

import java.util.List;

/**
 * 菜单导航响应DTO
 */
@Data
public class MenuNavigationResponse {

    private String tradeType;

    private String subType;

    private String description;

    private List<String> menuPath;

    private String routePath;

    private String id;

    /**
     * 是否需要进一步澄清（多轮反问）
     */
    private Boolean needClarification;

    /**
     * 澄清问题文案
     */
    private String clarificationQuestion;

    /**
     * 澄清问题可选项（快捷回复），例如 ["远期", "掉期"]
     */
    private List<String> clarificationOptions;

    /**
     * 当前澄清的维度：TRADE_TYPE（交易类型） / SUB_TYPE（子交易类型）
     */
    private String clarificationField;

    /**
     * 本轮已识别到的交易类型
     */
    private String matchedTradeType;

    /**
     * 本轮已识别到的子交易类型
     */
    private String matchedSubType;
}
