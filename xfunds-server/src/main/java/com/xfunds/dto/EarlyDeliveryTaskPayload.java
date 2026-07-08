package com.xfunds.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class EarlyDeliveryTaskPayload {
    /** 原交易ID */
    private String originalTradeId;
    /** 原业务编号 */
    private String originalBusinessNo;
    /** 原交易类型 */
    private String originalTradeType;
    /** 客户ID */
    private String customerId;
    /** 客户名称 */
    private String customerName;
    /** 货币对 */
    private String currencyPair;
    /** 原交易方向 */
    private String originalTradeDirection;
    /** 原交易金额 */
    private BigDecimal originalAmount;
    /** 原交易汇率 */
    private BigDecimal originalCustomerRate;
    /** 原到期日 */
    private LocalDate originalMaturityDate;
    /** 交易机构编码 */
    private String branchCode;
    /** 交易机构名称 */
    private String branchName;

    /** 近端客户汇率 */
    private BigDecimal nearLegCustomerRate;
    /** 近端成本汇率 */
    private BigDecimal nearLegCostRate;
    /** 近端交割方式 */
    private String nearLegSettlementMethod;
    /** 币种1账户 */
    private String nearLegAccount1;
    /** 币种2账户 */
    private String nearLegAccount2;

    /** 远端客户汇率 */
    private BigDecimal farLegCustomerRate;
    /** 远端成本汇率 */
    private BigDecimal farLegCostRate;
    /** 远端交割方式 */
    private String farLegSettlementMethod;

    /** 近端起息日 */
    private LocalDate nearLegValueDate;
    /** 远端到期日 */
    private LocalDate farLegValueDate;

    /** 备注 */
    private String remark;
}
