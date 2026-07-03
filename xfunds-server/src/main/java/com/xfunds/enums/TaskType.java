package com.xfunds.enums;

import lombok.Getter;

/**
 * 任务类型枚举
 */
@Getter
public enum TaskType {

    CHECK("复核"),
    AUTHORIZE("授权"),
    CHECK_LIFECYCLE("生命周期复核"),
    MARGIN_CALL("追保"),
    MATURITY_REMIND("到期提醒"),
    EXERCISE_REMIND("行权提醒"),
    QUOTE_CHECK("报价复核"),
    MODIFY("修改交易"),
    EARLY_DEFAULT("提前违约"),
    EARLY_DELIVERY("提前交割");

    private final String description;

    TaskType(String description) {
        this.description = description;
    }
}
