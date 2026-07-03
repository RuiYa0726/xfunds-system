package com.xfunds.enums;

import lombok.Getter;

/**
 * 交割类型枚举（T+0/T+1/T+2）
 */
@Getter
public enum DeliveryType {

    T0("T+0"),
    T1("T+1"),
    T2("T+2");

    private final String description;

    DeliveryType(String description) {
        this.description = description;
    }
}
