package com.xfunds.service;

import com.xfunds.dto.EarlyDefaultRequest;
import com.xfunds.dto.EarlyDeliveryRequest;
import com.xfunds.dto.FullDefaultRequest;
import com.xfunds.dto.MarginSupplementRequest;
import com.xfunds.dto.RolloverMarketRequest;
import com.xfunds.dto.RolloverRequest;

/**
 * 交易生命周期操作服务接口
 * 涵盖审批流程（复核通过/拒绝/退回）与未到期交易操作
 */
public interface TradeLifecycleOpsService {

    /**
     * 复核通过：更新交易状态并记录审批日志与生命周期事件
     *
     * @param taskId  复核任务ID
     * @param tradeId 交易ID
     * @param comment 审批意见
     * @return 新生成的交易ID（如需授权则返回原交易ID）
     */
    String approveTrade(Long taskId, String tradeId, String comment);

    /**
     * 复核拒绝：交易状态置为已拒绝
     *
     * @param taskId  复核任务ID
     * @param tradeId 交易ID
     * @param comment 审批意见
     */
    void rejectTrade(Long taskId, String tradeId, String comment);

    /**
     * 退回经办：交易状态回退为草稿
     *
     * @param taskId  复核任务ID
     * @param tradeId 交易ID
     * @param comment 审批意见
     */
    void returnTrade(Long taskId, String tradeId, String comment);

    /**
     * 提前交割：原交易置为提前交割，生成一笔掉期交易，处理违约金
     *
     * @param tradeId 原交易ID
     * @param request 提前交割请求
     * @return 新生成的掉期交易ID
     */
    String earlyDelivery(String tradeId, EarlyDeliveryRequest request);

    /**
     * 提前违约：原交易置为提前违约，生成即期与掉期各一笔，处理违约金
     *
     * @param tradeId 原交易ID
     * @param request 提前违约请求
     * @return 新生成的即期交易ID
     */
    String earlyDefault(String tradeId, EarlyDefaultRequest request);

    /**
     * 原价展期：原交易置为已展期，生成一笔掉期交易，释放违约金
     *
     * @param tradeId 原交易ID
     * @param request 原价展期请求
     * @return 新生成的掉期交易ID
     */
    String rolloverOriginal(String tradeId, RolloverRequest request);

    /**
     * 市价展期：原交易置为已平仓，生成一笔掉期交易，处理盈亏
     *
     * @param tradeId 原交易ID
     * @param request 市价展期请求
     * @return 新生成的掉期交易ID
     */
    String rolloverMarket(String tradeId, RolloverMarketRequest request);

    /**
     * 保证金增补：增加保证金账户余额
     *
     * @param tradeId 原交易ID
     * @param request 保证金增补请求
     */
    void marginSupplement(String tradeId, MarginSupplementRequest request);

    /**
     * 全部违约：原交易置为提前违约，扣减违约金
     *
     * @param tradeId 原交易ID
     * @param request 全部违约请求
     */
    void fullDefault(String tradeId, FullDefaultRequest request);

}
