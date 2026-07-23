package com.xfunds.mapper;

import com.xfunds.entity.FxQuote;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 报价 Mapper 接口
 */
@Mapper
public interface FxQuoteMapper {

    /**
     * 根据报价ID查询报价
     */
    FxQuote selectByQuoteId(Long quoteId);

    /**
     * 根据报价类型和货币对查询报价列表
     */
    List<FxQuote> selectByTypeAndPair(String quoteType, String currencyPair);

    /**
     * 查询所有报价
     */
    List<FxQuote> selectAll();

    /**
     * 根据报价类型查询有效报价（status='1'），按货币对排序
     */
    List<FxQuote> selectByType(String quoteType);

    /**
     * 根据条件分页查询报价列表
     * params 包含 quoteType, currencyPair, status, offset, size
     */
    List<FxQuote> selectByCondition(Map<String, Object> params);

    /**
     * 根据条件统计报价总数
     * params 包含 quoteType, currencyPair, status
     */
    long countByCondition(Map<String, Object> params);

    /**
     * 新增报价
     */
    int insert(FxQuote fxQuote);

    /**
     * 根据报价ID更新报价全部字段
     */
    int update(FxQuote fxQuote);

    /**
     * 查询所有有效报价（status='ACTIVE'），不分类型
     */
    List<FxQuote> selectAllActive();

    /**
     * 将所有有效报价置为失效（status -> INACTIVE）
     */
    int deactivateAllActive();

    /**
     * 删除创建时间早于指定天数的报价记录
     */
    int deleteOlderThanDays(@Param("days") int days);

    /**
     * 查询指定货币对、报价类型近 N 天的汇率序列（按 effective_time 升序）
     * 用于汇率趋势分析，包含 ACTIVE 和 INACTIVE 历史报价
     *
     * @param quoteType    报价类型，如 SPOT
     * @param currencyPair 货币对，如 USD/CNY
     * @param days         近 N 天
     */
    List<FxQuote> selectRecentRates(@Param("quoteType") String quoteType,
                                     @Param("currencyPair") String currencyPair,
                                     @Param("days") int days);
}
