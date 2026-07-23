package com.xfunds.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 提前交割请求 DTO
 */
@Data
public class EarlyDeliveryRequest {

    /** 原交易ID */
    @NotBlank(message = "交易ID不能为空")
    private String tradeId;

    /** 近端客户汇率 */
    private BigDecimal nearLegCustomerRate;

    /** 近端成本汇率 */
    private BigDecimal nearLegCostRate;

    /** 远端客户汇率 */
    private BigDecimal farLegCustomerRate;

    /** 远端成本汇率 */
    private BigDecimal farLegCostRate;

    /** 币种1账户 */
    private String nearLegAccount1;

    /** 币种2账户 */
    private String nearLegAccount2;

    /** 近端起息日 */
    private LocalDate nearLegValueDate;

    /** 远端到期日 */
    private LocalDate farLegValueDate;

    /** 备注 */
    private String remark;
}
