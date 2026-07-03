package com.xfunds.enums;

import lombok.Getter;

/**
 * 交易类型枚举
 */
@Getter
public enum TradeType {

    SPOT("即期"),
    FORWARD("远期"),
    SWAP("掉期"),
    OPTION("期权");

    private final String description;

    TradeType(String description) {
        this.description = description;
    }
}
