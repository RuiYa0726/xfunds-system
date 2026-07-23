package com.xfunds.dto;

import lombok.Data;

import java.util.List;

/**
 * 产品推荐响应 DTO
 */
@Data
public class ProductRecommendResponse {

    /** 客户ID */
    private String customerId;
    /** 客户名称 */
    private String customerName;
    /** 客户画像摘要 */
    private CustomerPortraitDTO portrait;
    /** 推荐产品列表（按优先级排序，最多Top 3） */
    private List<ProductRecommendDTO> recommendations;
    /** 是否为兜底推荐（无规则命中时） */
    private Boolean fallback;
}
