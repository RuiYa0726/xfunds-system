package com.xfunds.enums;

import lombok.Getter;

/**
 * 交割方式枚举（全额/差额/无需交割）
 */
@Getter
public enum SettlementMethod {

    FULL("全额交割"),
    NET("差额交割"),
    NONE("无需交割");

    private final String description;

    SettlementMethod(String description) {
        this.description = description;
    }
}
