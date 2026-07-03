package com.xfunds.enums;

import lombok.Getter;

/**
 * 任务状态枚举
 */
@Getter
public enum TaskStatus {

    PENDING("待处理"),
    CLAIMED("已认领"),
    DONE("已完成"),
    CANCELLED("已取消"),
    TIMEOUT("已超时");

    private final String description;

    TaskStatus(String description) {
        this.description = description;
    }
}
