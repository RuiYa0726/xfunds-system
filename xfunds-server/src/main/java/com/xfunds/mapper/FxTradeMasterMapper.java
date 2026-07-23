package com.xfunds.mapper;

import com.xfunds.entity.FxTradeMaster;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 交易主表 Mapper 接口
 */
@Mapper
public interface FxTradeMasterMapper {

    /**
     * 根据交易ID查询交易
     */
    FxTradeMaster selectByTradeId(String tradeId);

    /**
     * 根据业务编号查询交易
     */
    FxTradeMaster selectByBusinessNo(String businessNo);

    /**
     * 查询所有交易
     */
    List<FxTradeMaster> selectAll();

    /**
     * 按条件分页查询交易列表
     *
     * @param params 查询条件：businessNo、tradeType、status、specialTradeType、
     *               currencyPair、branchCode、customerId、statusList、tradeTypeList、offset、pageSize
     */
    List<FxTradeMaster> selectByCondition(Map<String, Object> params);

    /**
     * 按条件查询交易总数
     */
    long countByCondition(Map<String, Object> params);

    /**
     * 查询需要定时交割的交易：到期日 <= 指定日期 且 状态在给定状态列表内
     *
     * @param maturityDate 到期日上界（含当天）
     * @param statusList   需处理的状态列表（如 ACTIVE、MATURED）
     */
    List<FxTradeMaster> selectToSettle(@Param("maturityDate") LocalDate maturityDate,
                                      @Param("statusList") List<String> statusList);

    /**
     * 新增交易
     */
    int insert(FxTradeMaster trade);

    /**
     * 更新交易（仅更新状态、复核人、复核时间，带乐观锁）
     */
    int update(FxTradeMaster trade);

    /**
     * 更新交易全部字段（用于退回重新编辑提交场景），带乐观锁
     */
    int updateAllFields(FxTradeMaster trade);

    /**
     * 更新交易交割方式
     */
    int updateSettlementMethod(@Param("tradeId") String tradeId,
                               @Param("settlementMethod") String settlementMethod);

    /**
     * 查询客户近12个月有效交易的 RFM 指标
     * 返回 Map 包含：last_trade_date, recency_days, frequency_12m, monetary_12m_cny
     *
     * @param customerId 客户ID
     */
    Map<String, Object> selectRfmByCustomerId(@Param("customerId") String customerId);

    /**
     * 查询客户全部有效交易（用于敞口方向、衍生品资质推断）
     *
     * @param customerId 客户ID
     */
    List<FxTradeMaster> selectValidByCustomerId(@Param("customerId") String customerId);
}
