package com.xfunds.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 保证金参数实体
 */
@Data
public class FxMarginParam {

    /** 参数ID */
    private Long paramId;
    /** 参数编码 */
    private String paramCode;
    /** 参数名称 */
    private String paramName;
    /** 参数值 */
    private String paramValue;
    /** 币种 */
    private String currency;
    /** 交易类型 */
    private String tradeType;
    /** 描述 */
    private String description;
    /** 更新时间 */
    private LocalDateTime updatedAt;
}
