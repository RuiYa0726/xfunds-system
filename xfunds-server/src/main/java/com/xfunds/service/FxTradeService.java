package com.xfunds.service;

import com.xfunds.dto.ForwardTradeEntryRequest;
import com.xfunds.dto.PageResponse;
import com.xfunds.dto.SpotTradeEntryRequest;
import com.xfunds.dto.SwapTradeEntryRequest;
import com.xfunds.dto.TradeDetailVO;
import com.xfunds.dto.TradeResponse;
import com.xfunds.entity.FxTradeMaster;

import java.util.List;

/**
 * 交易服务接口
 */
public interface FxTradeService {

    /**
     * 根据交易ID查询交易
     */
    FxTradeMaster getByTradeId(String tradeId);

    /**
     * 查询所有交易
     */
    List<FxTradeMaster> listAll();

    /**
     * 创建即期交易（录入后自动提交复核）
     *
     * @param req 即期交易录入请求
     * @return 交易ID
     */
    String createSpotTrade(SpotTradeEntryRequest req);

    /**
     * 创建远期交易（录入后自动提交复核）
     *
     * @param req 远期交易录入请求
     * @return 交易ID
     */
    String createForwardTrade(ForwardTradeEntryRequest req);

    /**
     * 创建掉期交易（录入后自动提交复核）
     *
     * @param req 掉期交易录入请求
     * @return 交易ID
     */
    String createSwapTrade(SwapTradeEntryRequest req);

    /**
     * 查询交易完整详情（主表 + 子表明细 + 生命周期 + 审批日志）
     */
    TradeDetailVO getTradeDetail(String tradeId);

    /**
     * 分页查询未到期交易（状态为生效且类型为远期/掉期）
     * tab=swapNear 过滤起息日 > 今天，tab=swapFar 过滤到期日 > 今天
     */
    PageResponse<FxTradeMaster> listUnmatured(String businessNo, String tradeType, String currencyPair,
                                              String branchCode, String customerId, String tab, int pageNum, int pageSize);

    /**
     * 分页查询客户交易（支持多条件过滤）
     */
    PageResponse<TradeResponse> listCustomerTrades(String businessNo, String customerId, String tradeType,
                                                   String status, String specialTradeType,
                                                   int pageNum, int pageSize);

    /**
     * 更新即期交易并重新提交复核
     *
     * @param tradeId 交易ID
     * @param req 即期交易录入请求
     * @return 交易ID
     */
    String updateAndResubmitSpotTrade(String tradeId, SpotTradeEntryRequest req);

    /**
     * 更新远期交易并重新提交复核
     *
     * @param tradeId 交易ID
     * @param req 远期交易录入请求
     * @return 交易ID
     */
    String updateAndResubmitForwardTrade(String tradeId, ForwardTradeEntryRequest req);

    /**
     * 更新掉期交易并重新提交复核
     *
     * @param tradeId 交易ID
     * @param req 掉期交易录入请求
     * @return 交易ID
     */
    String updateAndResubmitSwapTrade(String tradeId, SwapTradeEntryRequest req);
}
