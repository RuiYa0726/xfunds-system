package com.xfunds.entity;

import lombok.Data;

/**
 * 字段别名表（辅助自然语言解析）
 */
@Data
public class ReportFieldAlias {

    private Long id;
    private String fieldCode;
    /** 别名/同义词 */
    private String aliasWord;
    /** 类型：STANDARD 标准名 / SYNONYM 同义词 */
    private String aliasType;
}
