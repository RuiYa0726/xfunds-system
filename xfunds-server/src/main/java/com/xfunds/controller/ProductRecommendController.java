package com.xfunds.controller;

import com.xfunds.common.Result;
import com.xfunds.common.ResultCode;
import com.xfunds.dto.ProductRecommendResponse;
import com.xfunds.service.ProductRecommendService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户产品推荐控制器
 */
@RestController
@RequestMapping("/api/product-recommend")
public class ProductRecommendController {

    private final ProductRecommendService productRecommendService;

    public ProductRecommendController(ProductRecommendService productRecommendService) {
        this.productRecommendService = productRecommendService;
    }

    /**
     * 根据客户ID获取产品推荐
     * 后端实时计算客户画像（RFM/AUM/KYC/敞口），执行合规过滤与规则匹配，返回Top 3推荐产品及理由
     * 若客户不存在，返回业务错误码 4004 及提示信息
     */
    @GetMapping("/recommend")
    public Result<ProductRecommendResponse> recommend(@RequestParam String customerId) {
        try {
            ProductRecommendResponse response = productRecommendService.recommendProducts(customerId);
            return Result.ok(response);
        } catch (IllegalArgumentException e) {
            return Result.fail(ResultCode.PARAM_ERROR.getCode(),
                    "客户号 " + customerId + " 不存在，请检查客户号后重试");
        }
    }
}
