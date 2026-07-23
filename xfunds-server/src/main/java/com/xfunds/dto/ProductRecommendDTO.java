package com.xfunds.dto;

import lombok.Data;

/**
 * 单个产品推荐结果 DTO
 */
@Data
public class ProductRecommendDTO {

    /** 产品编码 */
    private String productCode;
    /** 产品名称 */
    private String productName;
    /** 推荐优先级（数值越小越优先） */
    private Integer priority;
    /** 推荐理由 */
    private String reason;
    /** 匹配的场景名称 */
    private String scenario;
    /** 产品风险层级：BASIC基础/STANDARD标准/COMPLEX复杂 */
    private String riskTier;
}
