package com.xfunds.service;

import com.xfunds.dto.ProductRecommendResponse;

/**
 * 客户产品推荐服务接口
 * 基于客户交易行为（RFM）、资产规模（AUM）、风险等级（KYC）及敞口/行业特征，
 * 为客户推荐最适合的外汇交易产品
 */
public interface ProductRecommendService {

    /**
     * 根据客户ID计算画像并生成产品推荐
     *
     * @param customerId 客户ID
     * @return 推荐结果（含客户画像摘要 + Top 3 推荐产品及理由）
     */
    ProductRecommendResponse recommendProducts(String customerId);
}
