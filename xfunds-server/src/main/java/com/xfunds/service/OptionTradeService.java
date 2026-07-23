package com.xfunds.service;

import com.xfunds.dto.OptionAbandonRequest;
import com.xfunds.dto.OptionExerciseRequest;
import com.xfunds.dto.OptionPremiumSettleRequest;
import com.xfunds.dto.OptionReminderVO;
import com.xfunds.dto.OptionTradeDetailVO;
import com.xfunds.dto.OptionTradeEntryRequest;
import com.xfunds.dto.PageResponse;
import com.xfunds.entity.FxTradeMaster;

import java.util.List;
import java.util.Map;

/**
 * 期权交易服务接口
 */
public interface OptionTradeService {

    String createOption(OptionTradeEntryRequest req);

    void handleOptionApproval(FxTradeMaster master, Long operatorId);

    /**
     * 放弃期权审批通过处理：解冻账户余额、设置放弃标志与放弃日、更新交易状态为已放弃
     * @param tradeId 交易ID
     * @param operatorId 操作人（审批人）ID
     * @param comment 审批意见
     */
    void processAbandonApproval(String tradeId, Long operatorId, String comment);

    /**
     * 执行期权审批通过处理：扣除币种1账户冻结的面值金额、设置行权标志与行权日、更新交易状态为已行权
     * @param tradeId 交易ID
     * @param operatorId 操作人（审批人）ID
     * @param comment 审批意见
     * @param payloadJson 提交时携带的行权参数JSON（含参考汇率、交割账户等）
     */
    void processExerciseApproval(String tradeId, Long operatorId, String comment, String payloadJson);

    OptionTradeDetailVO getOptionDetail(String tradeId);

    /**
     * 分页查询期权交易列表（返回主表+期权子表合并字段）
     */
    PageResponse<Map<String, Object>> listOptions(String businessNo, String customerId, String optionStyle,
                                            String optionType, String status, int pageNum, int pageSize);

    /**
     * 查询期权费已交割的期权交易列表
     */
    PageResponse<Map<String, Object>> listPremiumTrades(String businessNo, String customerId, int pageNum, int pageSize);

    /**
     * 查询已行权的期权交易列表
     */
    PageResponse<Map<String, Object>> listExerciseTrades(String businessNo, String customerId, int pageNum, int pageSize);

    /**
     * 查询已放弃的期权交易列表
     */
    PageResponse<Map<String, Object>> listAbandonTrades(String businessNo, String customerId, int pageNum, int pageSize);

    // ==================== 期权工作台 ====================

    /**
     * 查询期权价内提醒列表：牌价达到执行价格的所有期权（不限美式/欧式）
     */
    List<OptionReminderVO> getInMoneyReminders();

    OptionTradeDetailVO viewOriginalTrade(String tradeId);

    void executeOption(String tradeId, OptionExerciseRequest request);

    void postponeReminder(String tradeId, String remark);

    // ==================== 期权存续期管理 ====================

    PageResponse<FxTradeMaster> listUnmaturedOptions(String businessNo, String customerId, String optionStyle,
                                                     int pageNum, int pageSize);

    PageResponse<FxTradeMaster> listEuropeanMaturedOptions(String businessNo, String customerId,
                                                           int pageNum, int pageSize);

    void abandonOption(String tradeId, OptionAbandonRequest request);

    void premiumSettle(String tradeId, OptionPremiumSettleRequest request);

    PageResponse<FxTradeMaster> listAmericanMonitoring(String businessNo, String customerId,
                                                       int pageNum, int pageSize);

    PageResponse<FxTradeMaster> listAmericanMaturedOptions(String businessNo, String customerId,
                                                           int pageNum, int pageSize);

    /**
     * 牌价刷新时更新期权参考汇率（用于价内提醒监听）
     */
    void updateReferenceRates();
}
