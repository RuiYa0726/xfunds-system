package com.xfunds.service;

import com.xfunds.dto.OptionAbandonRequest;
import com.xfunds.dto.OptionCloseRequest;
import com.xfunds.dto.OptionExerciseRequest;
import com.xfunds.dto.OptionPremiumSettleRequest;
import com.xfunds.dto.OptionReminderVO;
import com.xfunds.dto.OptionTradeDetailVO;
import com.xfunds.dto.OptionTradeEntryRequest;
import com.xfunds.dto.PageResponse;
import com.xfunds.entity.FxTradeMaster;

import java.util.List;

/**
 * 期权交易服务接口
 */
public interface OptionTradeService {

    /**
     * 创建期权交易（录入后自动提交复核）
     *
     * @param req 期权交易录入请求
     * @return 交易ID
     */
    String createOption(OptionTradeEntryRequest req);

    /**
     * 查询期权交易完整详情（主表 + 期权子表明细 + 生命周期 + 审批日志）
     *
     * @param tradeId 交易ID
     * @return 期权交易详情
     */
    OptionTradeDetailVO getOptionDetail(String tradeId);

    /**
     * 分页查询期权交易列表（支持业务编号、客户ID、行权方式、期权类型、状态过滤）
     *
     * @param businessNo   业务编号
     * @param customerId   客户ID
     * @param optionStyle  行权方式
     * @param optionType   期权类型
     * @param status       交易状态
     * @param pageNum      页码
     * @param pageSize     每页大小
     * @return 分页结果
     */
    PageResponse<FxTradeMaster> listOptions(String businessNo, String customerId, String optionStyle,
                                            String optionType, String status, int pageNum, int pageSize);

    /**
     * 查询已平仓的期权交易列表
     */
    PageResponse<FxTradeMaster> listCloseTrades(String businessNo, String customerId, int pageNum, int pageSize);

    /**
     * 查询期权费已交割的期权交易列表
     */
    PageResponse<FxTradeMaster> listPremiumTrades(String businessNo, String customerId, int pageNum, int pageSize);

    /**
     * 查询已行权的期权交易列表
     */
    PageResponse<FxTradeMaster> listExerciseTrades(String businessNo, String customerId, int pageNum, int pageSize);

    /**
     * 查询已放弃的期权交易列表
     */
    PageResponse<FxTradeMaster> listAbandonTrades(String businessNo, String customerId, int pageNum, int pageSize);

    // ==================== 期权工作台 ====================

    /**
     * 查询美式期权实值提醒列表（处于生效/期权费已结清状态且为实值）
     *
     * @return 提醒视图列表
     */
    List<OptionReminderVO> getAmericanInMoneyReminders();

    /**
     * 查看原始期权交易详情
     *
     * @param tradeId 交易ID
     * @return 期权交易详情
     */
    OptionTradeDetailVO viewOriginalTrade(String tradeId);

    /**
     * 行权：美式期权到期前可随时行权，欧式期权到期日及以后可行权
     *
     * @param tradeId 交易ID
     * @param request 行权请求
     */
    void executeOption(String tradeId, OptionExerciseRequest request);

    /**
     * 暂不处理：仅记录生命周期事件，不改变交易状态
     *
     * @param tradeId 交易ID
     * @param remark  备注
     */
    void postponeReminder(String tradeId, String remark);

    // ==================== 期权存续期管理 ====================

    /**
     * 分页查询未到期期权交易（状态为生效或期权费已结清且到期日大于今天）
     */
    PageResponse<FxTradeMaster> listUnmaturedOptions(String businessNo, String customerId, String optionStyle,
                                                     int pageNum, int pageSize);

    /**
     * 平仓：更新平仓信息，若剩余金额为0则状态置为已平仓
     *
     * @param tradeId 交易ID
     * @param request 平仓请求
     */
    void closeOption(String tradeId, OptionCloseRequest request);

    /**
     * 分页查询已到期的欧式期权交易（状态为期权费已结清且到期日小于等于今天）
     */
    PageResponse<FxTradeMaster> listEuropeanMaturedOptions(String businessNo, String customerId,
                                                           int pageNum, int pageSize);

    /**
     * 放弃：更新放弃标志，状态置为已放弃
     *
     * @param tradeId 交易ID
     * @param request 放弃请求
     */
    void abandonOption(String tradeId, OptionAbandonRequest request);

    /**
     * 期权费交割：更新期权费支付标志与账户，状态从生效转为期权费已结清
     *
     * @param tradeId 交易ID
     * @param request 期权费交割请求
     */
    void premiumSettle(String tradeId, OptionPremiumSettleRequest request);

    /**
     * 分页查询美式期权监控期交易（状态为期权费已结清且到期日大于今天）
     */
    PageResponse<FxTradeMaster> listAmericanMonitoring(String businessNo, String customerId,
                                                       int pageNum, int pageSize);

    /**
     * 分页查询已到期的美式期权交易（状态为期权费已结清且到期日小于等于今天）
     */
    PageResponse<FxTradeMaster> listAmericanMaturedOptions(String businessNo, String customerId,
                                                           int pageNum, int pageSize);
}
