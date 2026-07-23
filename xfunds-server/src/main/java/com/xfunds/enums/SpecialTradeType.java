package com.xfunds.enums;

import lombok.Getter;

/**
 * 特殊交易类型枚举
 */
@Getter
public enum SpecialTradeType {

    NORMAL("正常"),
    EARLY_DEFAULT("提前违约"),
    MATURITY_DEFAULT("到期违约"),
    ROLLOVER_ORIGINAL("展期原交易"),
    ROLLOVER_MARKET("展期市价交易"),
    EARLY_DELIVERY("提前交割"),
    FULL_DEFAULT("全部违约");

    private final String description;

    SpecialTradeType(String description) {
        this.description = description;
    }
}
