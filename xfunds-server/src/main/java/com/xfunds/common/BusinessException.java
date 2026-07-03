package com.xfunds.common;

import lombok.Getter;

/**
 * 自定义业务异常
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    /**
     * 使用响应码构造业务异常
     */
    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    /**
     * 使用响应码和自定义消息构造业务异常
     */
    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    /**
     * 使用自定义码和消息构造业务异常
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
