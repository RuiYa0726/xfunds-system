package com.xfunds.enums;

import lombok.Getter;

/**
 * 交易方向枚举（买入/卖出）
 */
@Getter
public enum TradeDirection {

    BUY("买入"),
    SELL("卖出");

    private final String description;

    TradeDirection(String description) {
        this.description = description;
    }
}
