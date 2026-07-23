package com.xfunds.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 客户画像 DTO
 * 包含 RFM 行为标签、AUM 资产标签、KYC 风险等级等画像维度
 */
@Data
public class CustomerPortraitDTO {

    /** 客户ID */
    private String customerId;
    /** 客户名称 */
    private String customerName;
    /** 客户类型：CORP/RETAIL */
    private String customerType;
    /** 企业类型（经营模式划分）：MANUFACTURING/TRADING/SERVICE/INVESTMENT_HOLDING/FINANCIAL_INSTITUTION/SME */
    private String corpType;
    /** 信用等级 */
    private String creditLevel;
    /** 风险等级：AGGRESSIVE激进/BALANCED平衡/CONSERVATIVE保守 */
    private String riskLevel;

    /** 最近交易间隔天数 */
    private Integer recencyDays;
    /** 近12个月交易笔数 */
    private Integer frequency12m;
    /** 近12个月交易总额（CNY） */
    private BigDecimal monetary12mCny;

    /** R 标签 */
    private String rLabel;
    /** F 标签 */
    private String fLabel;
    /** M 标签 */
    private String mLabel;
    /** RFM 组合编码 */
    private String rfmSegment;

    /** 总资产（CNY，不含冻结） */
    private BigDecimal aumTotalCny;
    /** AUM 标签 */
    private String aumLabel;

    /** 是否通过衍生品适当性认证（基于历史交易推断） */
    private Boolean hasDerivativeLicense;
    /** 行业标签（基于客户名推断） */
    private String industry;
    /** 敞口类型：PAYABLE应付/RECEIVABLE应收/DUAL双向/NONE无 */
    private String exposureType;

    /** 汇率趋势：USD_UP美元升值/USD_DOWN美元贬值/FLAT震荡/UNKNOWN数据不足 */
    private String rateTrend;
    /** 汇率趋势展示标签（含当前汇率、涨跌幅描述） */
    private String rateTrendLabel;
    /** 当前 USD/CNY 汇率（最近交易日客户汇率） */
    private BigDecimal currentRate;
    /** 近30天汇率涨跌幅（百分比，正为美元升值，负为美元贬值） */
    private BigDecimal rateChangePct;

    /** 画像更新时间 */
    private String tagUpdateTime;
}
