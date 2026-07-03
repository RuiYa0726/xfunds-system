package com.xfunds.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 报价实体
 */
@Data
public class FxQuote {

    /** 报价ID */
    private Long quoteId;
    /** 报价类型：SPOT/FORWARD/SWAP */
    private String quoteType;
    /** 货币对 */
    private String currencyPair;
    /** 基础货币 */
    private String baseCurrency;
    /** 报价货币 */
    private String quoteCurrency;
    /** 期限（远期/掉期如 1M/3M） */
    private String term;
    /** 到期日 */
    private LocalDate maturityDate;
    /** 市场中间价 */
    private BigDecimal marketMidRate;
    /** 总行买入价 */
    private BigDecimal totalBuyRate;
    /** 总行卖出价 */
    private BigDecimal totalSellRate;
    /** 分/客买价（分行买入价=客户卖出价，银行买入外币的汇率） */
    private BigDecimal branchCustomerBuyRate;
    /** 分/客卖价（分行卖出价=客户买入价，银行卖出外币的汇率） */
    private BigDecimal branchCustomerSellRate;
    /** 远期点 */
    private BigDecimal forwardPoint;
    /** 掉期点 */
    private BigDecimal swapPoint;
    /** 状态 */
    private String status;
    /** 生效时间 */
    private LocalDateTime effectiveTime;
    /** 发布人 */
    private Long publishedBy;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 更新时间 */
    private LocalDateTime updatedAt;
}
