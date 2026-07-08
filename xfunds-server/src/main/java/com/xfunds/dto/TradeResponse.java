package com.xfunds.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 交易响应 DTO
 */
@Data
public class TradeResponse {

    /** 交易ID */
    private String tradeId;
    /** 业务编号 */
    private String businessNo;
    /** 交易类型 */
    private String tradeType;
    /** 交易状态 */
    private String status;
    /** 客户名称 */
    private String customerName;
    /** 货币对 */
    private String currencyPair;
    /** 名义金额 */
    private String notionalAmount;
    /** 交易方向 */
    private String tradeDirection;
    /** 客户汇率 */
    private String customerRate;
    /** 交易日 */
    private String tradeDate;
    /** 交易时间（24小时制） */
    private LocalDateTime tradeTime;
    /** 到期日 */
    private String maturityDate;
    /** 交割方式：FULL/NET/NONE */
    private String settlementMethod;
    /** 特殊交易类型 */
    private String specialTradeType;
    /** 原交易类型 */
    private String originalTradeType;
    /** 经办人ID */
    private Long makerId;
    /** 复核人ID */
    private Long checkerId;
    /** 经办人姓名 */
    private String makerName;
    /** 复核人姓名 */
    private String checkerName;
    /** 创建时间 */
    private LocalDateTime createdAt;
}
