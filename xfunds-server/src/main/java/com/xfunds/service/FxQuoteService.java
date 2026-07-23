package com.xfunds.service;

import com.xfunds.dto.PageResponse;
import com.xfunds.entity.FxQuote;

import java.math.BigDecimal;
import java.util.List;

/**
 * 报价服务接口
 */
public interface FxQuoteService {

    /**
     * 根据报价类型和货币对查询报价列表
     */
    List<FxQuote> listByTypeAndPair(String quoteType, String currencyPair);

    /**
     * 查询所有报价
     */
    List<FxQuote> listAll();

    /**
     * 根据报价类型查询有效报价列表
     */
    List<FxQuote> listByType(String quoteType);

    /**
     * 查询所有有效即期报价
     */
    List<FxQuote> listSpot();

    /**
     * 查询所有有效远期报价
     */
    List<FxQuote> listForward();

    /**
     * 查询所有有效掉期报价
     */
    List<FxQuote> listSwap();

    /**
     * 根据报价ID查询报价详情
     */
    FxQuote getByQuoteId(Long quoteId);

    /**
     * 新增或更新报价（quoteId 为空则新增，否则更新）
     */
    FxQuote saveQuote(FxQuote fxQuote);

    /**
     * 根据条件分页查询报价列表
     */
    PageResponse<FxQuote> listByCondition(String quoteType, String currencyPair, String status,
                                          Integer pageNum, Integer pageSize);

    /**
     * 定时刷新牌价：模拟生成新牌价并更新展示，同时清理超过一周的历史牌价
     * 银行盈利约束：总/分买价 > 分/客买价，总/分卖价 < 分/客卖价
     */
    void refreshQuotes();

    /**
     * 查询指定币种对人民币的当日即期市场中间价
     * 用于客户账户折人民币余额计算：账户币种为 CNY 时返回 1，其他币种取 SPOT 报价 X/CNY 的市场中间价
     *
     * @param currency 账户币种（如 USD/EUR/CNY）
     * @return 该币种兑人民币的即期中间价；无对应报价时返回 null
     */
    BigDecimal getSpotMidRateToCny(String currency);
}
