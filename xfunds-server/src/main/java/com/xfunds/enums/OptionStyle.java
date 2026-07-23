package com.xfunds.enums;

import lombok.Getter;

/**
 * 期权行权方式枚举
 */
@Getter
public enum OptionStyle {

    AMERICAN("美式"),
    EUROPEAN("欧式");

    private final String description;

    OptionStyle(String description) {
        this.description = description;
    }
}
