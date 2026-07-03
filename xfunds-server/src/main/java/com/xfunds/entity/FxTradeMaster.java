package com.xfunds.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 交易主表实体
 */
@Data
public class FxTradeMaster {

    /** 交易ID */
    private String tradeId;
    /** 业务编号 */
    private String businessNo;
    /** 交易类型：SPOT/FORWARD/SWAP/OPTION */
    private String tradeType;
    /** 交易状态 */
    private String status;
    /** 分行编码 */
    private String branchCode;
    /** 分行名称（关联 fx_org.org_name，仅查询时使用） */
    private String branchName;
    /** 客户ID */
    private String customerId;
    /** 客户名称 */
    private String customerName;
    /** 基础货币 */
    private String baseCurrency;
    /** 报价货币 */
    private String quoteCurrency;
    /** 货币对 */
    private String currencyPair;
    /** 名义金额 */
    private BigDecimal notionalAmount;
    /** 对手金额 */
    private BigDecimal counterAmount;
    /** 交易方向：BUY/SELL */
    private String tradeDirection;
    /** 起息日 */
    private LocalDate valueDate;
    /** 交易日 */
    private LocalDate tradeDate;
    /** 到期日 */
    private LocalDate maturityDate;
    /** 交割类型：T0/T1/T2 */
    private String deliveryType;
    /** 交割方式：FULL/NET */
    private String settlementMethod;
    /** 即期汇率 */
    private BigDecimal spotRate;
    /** 客户汇率 */
    private BigDecimal customerRate;
    /** 成本汇率 */
    private BigDecimal costRate;
    /** 分行利润点 */
    private BigDecimal branchProfitPoint;
    /** 特殊交易类型 */
    private String specialTradeType;
    /** 原交易ID（展期/提前交割时引用） */
    private String originalTradeId;
    /** 原交易类型 */
    private String originalTradeType;
    /** 关联交易ID */
    private String relatedTradeId;
    /** 轧差货币 */
    private String nettingCurrency;
    /** 轧差账户 */
    private String nettingAccount;
    /** 轧差金额 */
    private BigDecimal nettingAmount;
    /** 经办人ID */
    private Long makerId;
    /** 复核人ID */
    private Long checkerId;
    /** 授权人ID */
    private Long authorizerId;
    /** 经办时间 */
    private LocalDateTime makeTime;
    /** 复核时间 */
    private LocalDateTime checkTime;
    /** 授权时间 */
    private LocalDateTime authorizeTime;
    /** 用途编码 */
    private String purposeCode;
    /** 外汇用途编码 */
    private String fxPurposeCode;
    /** RCPMIS上报标志 */
    private String rcpmisReportFlag;
    /** RCPMIS上报时间 */
    private LocalDateTime rcpmisReportTime;
    /** 版本号 */
    private Integer version;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 更新时间 */
    private LocalDateTime updatedAt;
}
