package com.xfunds.service.impl;

import com.xfunds.common.BusinessException;
import com.xfunds.common.ResultCode;
import com.xfunds.dto.PageResponse;
import com.xfunds.entity.FxQuote;
import com.xfunds.mapper.FxQuoteMapper;
import com.xfunds.service.FxQuoteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 报价服务实现类
 */
@Service
public class FxQuoteServiceImpl implements FxQuoteService {

    private static final Logger log = LoggerFactory.getLogger(FxQuoteServiceImpl.class);

    @Autowired
    private FxQuoteMapper fxQuoteMapper;

    /**
     * 根据报价类型和货币对查询报价列表
     */
    @Override
    public List<FxQuote> listByTypeAndPair(String quoteType, String currencyPair) {
        return fxQuoteMapper.selectByTypeAndPair(quoteType, currencyPair);
    }

    /**
     * 查询所有报价
     */
    @Override
    public List<FxQuote> listAll() {
        return fxQuoteMapper.selectAll();
    }

    /**
     * 根据报价类型查询有效报价列表
     */
    @Override
    public List<FxQuote> listByType(String quoteType) {
        return fxQuoteMapper.selectByType(quoteType);
    }

    /**
     * 查询所有有效即期报价
     */
    @Override
    public List<FxQuote> listSpot() {
        return fxQuoteMapper.selectByType("SPOT");
    }

    /**
     * 查询所有有效远期报价
     */
    @Override
    public List<FxQuote> listForward() {
        return fxQuoteMapper.selectByType("FORWARD");
    }

    /**
     * 查询所有有效掉期报价
     */
    @Override
    public List<FxQuote> listSwap() {
        return fxQuoteMapper.selectByType("SWAP");
    }

    /**
     * 根据报价ID查询报价详情
     */
    @Override
    public FxQuote getByQuoteId(Long quoteId) {
        FxQuote quote = fxQuoteMapper.selectByQuoteId(quoteId);
        if (quote == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "报价不存在");
        }
        return quote;
    }

    /**
     * 新增或更新报价（quoteId 为空则新增，否则更新）
     */
    @Override
    @Transactional
    public FxQuote saveQuote(FxQuote fxQuote) {
        if (fxQuote.getQuoteId() == null) {
            fxQuoteMapper.insert(fxQuote);
        } else {
            // 更新前校验报价是否存在
            FxQuote existing = fxQuoteMapper.selectByQuoteId(fxQuote.getQuoteId());
            if (existing == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "报价不存在，无法更新");
            }
            fxQuoteMapper.update(fxQuote);
        }
        return fxQuote;
    }

    /**
     * 根据条件分页查询报价列表
     */
    @Override
    public PageResponse<FxQuote> listByCondition(String quoteType, String currencyPair, String status,
                                                  Integer pageNum, Integer pageSize) {
        // 参数兜底处理
        int page = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;
        int offset = (page - 1) * size;

        Map<String, Object> params = new HashMap<>();
        params.put("quoteType", quoteType);
        params.put("currencyPair", currencyPair);
        params.put("status", status);
        params.put("offset", offset);
        params.put("size", size);

        long total = fxQuoteMapper.countByCondition(params);
        List<FxQuote> list = fxQuoteMapper.selectByCondition(params);
        return new PageResponse<>(total, page, size, list);
    }

    /**
     * 定时刷新牌价：模拟生成新牌价并更新展示，同时清理超过一周的历史牌价
     * 银行盈利约束：总/分买价 > 分/客买价，总/分卖价 < 分/客卖价
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refreshQuotes() {
        // 1. 获取当前所有有效牌价作为基准
        List<FxQuote> currentQuotes = fxQuoteMapper.selectAllActive();
        if (currentQuotes == null || currentQuotes.isEmpty()) {
            log.warn("[牌价刷新] 当前无有效牌价，跳过刷新");
            return;
        }

        // 2. 将当前有效牌价置为失效
        fxQuoteMapper.deactivateAllActive();

        // 3. 基于当前牌价波动生成新牌价并插入
        int generated = 0;
        for (FxQuote base : currentQuotes) {
            FxQuote newQuote = generateFluctuatedQuote(base);
            fxQuoteMapper.insert(newQuote);
            generated++;
        }

        // 4. 删除超过一周的历史牌价
        int deleted = fxQuoteMapper.deleteOlderThanDays(7);

        log.info("[牌价刷新] 生成新牌价 {} 条，清理历史牌价 {} 条", generated, deleted);
    }

    /**
     * 查询指定币种对人民币的当日即期市场中间价
     * 账户币种为 CNY 时返回 1；其他币种取 SPOT 报价（币种/CNY）的市场中间价
     */
    @Override
    public BigDecimal getSpotMidRateToCny(String currency) {
        if (currency == null || currency.isEmpty()) {
            return null;
        }
        if ("CNY".equalsIgnoreCase(currency)) {
            return BigDecimal.ONE;
        }
        // 报价表中即期报价均为 X/CNY 形式（base=币种, quote=CNY）
        String currencyPair = currency.toUpperCase() + "/CNY";
        List<FxQuote> quotes = fxQuoteMapper.selectByTypeAndPair("SPOT", currencyPair);
        if (quotes == null || quotes.isEmpty()) {
            log.warn("[折人民币汇率] 未找到币种 {} 的即期报价 {}", currency, currencyPair);
            return null;
        }
        BigDecimal midRate = quotes.get(0).getMarketMidRate();
        if (midRate == null || midRate.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("[折人民币汇率] 币种 {} 的即期中间价无效：{}", currency, midRate);
            return null;
        }
        return midRate;
    }

    /**
     * 基于现有牌价波动生成新牌价
     * 确保银行盈利：总/分买价 > 分/客买价，总/分卖价 < 分/客卖价
     */
    private FxQuote generateFluctuatedQuote(FxQuote base) {
        FxQuote quote = new FxQuote();
        quote.setQuoteType(base.getQuoteType());
        quote.setCurrencyPair(base.getCurrencyPair());
        quote.setBaseCurrency(base.getBaseCurrency());
        quote.setQuoteCurrency(base.getQuoteCurrency());
        quote.setTerm(base.getTerm());
        quote.setStatus("ACTIVE");
        quote.setEffectiveTime(LocalDateTime.now());
        quote.setPublishedBy(1L); // 系统自动生成

        // 市场中间价在原基础上随机波动 ±0.5%（牌价精确到小数点后4位）
        BigDecimal oldMid = base.getMarketMidRate();
        if (oldMid == null || oldMid.compareTo(BigDecimal.ZERO) <= 0) {
            // 无中间价时用买卖价均值代替
            BigDecimal avg = base.getTotalBuyRate().add(base.getTotalSellRate())
                    .divide(new BigDecimal("2"), 4, RoundingMode.HALF_UP);
            oldMid = avg;
        }
        BigDecimal fluctuation = new BigDecimal(ThreadLocalRandom.current().nextDouble(-0.005, 0.005));
        BigDecimal newMid = oldMid.multiply(BigDecimal.ONE.add(fluctuation))
                .setScale(4, RoundingMode.HALF_UP);
        quote.setMarketMidRate(newMid);

        // 价差：总行买卖价围绕中间价对称分布（0.2% 价差）
        BigDecimal spread = newMid.multiply(new BigDecimal("0.002")).setScale(4, RoundingMode.HALF_UP);
        BigDecimal totalBuyRate = newMid.subtract(spread);
        BigDecimal totalSellRate = newMid.add(spread);

        // 分行利润点：客户买价更低、卖价更高（0.3% 利润）
        BigDecimal branchProfit = newMid.multiply(new BigDecimal("0.003")).setScale(4, RoundingMode.HALF_UP);
        BigDecimal branchCustomerBuyRate = totalBuyRate.subtract(branchProfit);
        BigDecimal branchCustomerSellRate = totalSellRate.add(branchProfit);

        quote.setTotalBuyRate(totalBuyRate);
        quote.setTotalSellRate(totalSellRate);
        quote.setBranchCustomerBuyRate(branchCustomerBuyRate);
        quote.setBranchCustomerSellRate(branchCustomerSellRate);

        // 远期/掉期点数随机波动 ±5%
        if (base.getForwardPoint() != null) {
            BigDecimal fpFluctuation = new BigDecimal(ThreadLocalRandom.current().nextDouble(-0.05, 0.05));
            BigDecimal newForwardPoint = base.getForwardPoint().multiply(BigDecimal.ONE.add(fpFluctuation))
                    .setScale(4, RoundingMode.HALF_UP);
            quote.setForwardPoint(newForwardPoint);
        }
        if (base.getSwapPoint() != null) {
            BigDecimal spFluctuation = new BigDecimal(ThreadLocalRandom.current().nextDouble(-0.05, 0.05));
            BigDecimal newSwapPoint = base.getSwapPoint().multiply(BigDecimal.ONE.add(spFluctuation))
                    .setScale(4, RoundingMode.HALF_UP);
            quote.setSwapPoint(newSwapPoint);
        }

        // 根据期限重新计算到期日
        quote.setMaturityDate(calculateMaturityDate(base.getTerm()));

        return quote;
    }

    /**
     * 根据期限计算到期日（基于今天）
     */
    private LocalDate calculateMaturityDate(String term) {
        if (term == null || term.isEmpty()) {
            return null;
        }
        LocalDate today = LocalDate.now();
        switch (term) {
            case "ON": return today;
            case "TN": return today.plusDays(1);
            case "SN": return today.plusDays(2);
            case "SW": return today.plusDays(7);
            case "1D": return today.plusDays(1);
            case "1M": return today.plusMonths(1);
            case "3M": return today.plusMonths(3);
            case "6M": return today.plusMonths(6);
            default: return today.plusMonths(1);
        }
    }
}
