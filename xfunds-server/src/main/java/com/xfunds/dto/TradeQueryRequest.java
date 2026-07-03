package com.xfunds.dto;

import lombok.Data;

/**
 * 交易查询请求 DTO
 */
@Data
public class TradeQueryRequest {

    /** 当前页码 */
    private Integer pageNum = 1;
    /** 每页大小 */
    private Integer pageSize = 10;
    /** 交易类型 */
    private String tradeType;
    /** 交易状态 */
    private String status;
    /** 客户ID */
    private String customerId;
    /** 分行编码 */
    private String branchCode;
    /** 业务编号 */
    private String businessNo;
}
