package com.xfunds.enums;

import lombok.Getter;

/**
 * 报价类型枚举
 */
@Getter
public enum QuoteType {

    SPOT("即期"),
    FORWARD("远期"),
    SWAP("掉期");

    private final String description;

    QuoteType(String description) {
        this.description = description;
    }
}
