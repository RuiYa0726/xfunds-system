package com.xfunds.enums;

import lombok.Getter;

/**
 * 交易状态枚举
 */
@Getter
public enum TradeStatus {

    DRAFT("草稿"),
    PENDING_CHECK("待复核"),
    ACTIVE("生效"),
    MATURED("到期"),
    SETTLED("已交割"),
    SETTLE_FAILED("交割失败"),
    DEFAULTED("违约"),
    CLOSED("已平仓"),
    REJECTED("已拒绝"),
    ROLLED_OVER("已展期"),
    EARLY_SETTLED("提前交割"),
    EARLY_DEFAULTED("提前违约"),
    EXERCISED("已行权"),
    ABANDONED("已放弃"),
    PREMIUM_SETTLED("期权费已结清");

    private final String description;

    TradeStatus(String description) {
        this.description = description;
    }
}
