package com.xfunds.enums;

import lombok.Getter;

/**
 * 机构层级枚举
 */
@Getter
public enum OrgLevel {

    HEAD_OFFICE(1, "总行"),
    BRANCH(2, "分行"),
    SUB_BRANCH(3, "支行");

    private final int level;
    private final String description;

    OrgLevel(int level, String description) {
        this.level = level;
        this.description = description;
    }
}
