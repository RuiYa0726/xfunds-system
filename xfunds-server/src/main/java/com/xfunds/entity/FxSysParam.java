package com.xfunds.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统参数实体
 */
@Data
public class FxSysParam {

    /** 参数ID */
    private Long paramId;
    /** 参数编码 */
    private String paramCode;
    /** 参数名称 */
    private String paramName;
    /** 参数值 */
    private String paramValue;
    /** 参数类型 */
    private String paramType;
    /** 描述 */
    private String description;
    /** 更新时间 */
    private LocalDateTime updatedAt;
}
