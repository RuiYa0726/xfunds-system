package com.xfunds.enums;

import lombok.Getter;

/**
 * 期权类型枚举（看涨/看跌）
 */
@Getter
public enum OptionType {

    CALL("看涨"),
    PUT("看跌");

    private final String description;

    OptionType(String description) {
        this.description = description;
    }
}
