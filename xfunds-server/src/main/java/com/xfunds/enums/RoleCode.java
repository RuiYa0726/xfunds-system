package com.xfunds.enums;

import lombok.Getter;

/**
 * 角色编码枚举
 */
@Getter
public enum RoleCode {

    MAKER("经办"),
    CHECKER("复核"),
    AUTHORIZER("授权");

    private final String description;

    RoleCode(String description) {
        this.description = description;
    }
}
