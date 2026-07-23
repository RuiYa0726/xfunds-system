package com.xfunds.service.impl;

import com.xfunds.dto.CustomerPortraitDTO;
import com.xfunds.dto.ProductRecommendDTO;
import com.xfunds.dto.ProductRecommendResponse;
import com.xfunds.entity.FxCustomer;
import com.xfunds.entity.FxQuote;
import com.xfunds.entity.FxTradeMaster;
import com.xfunds.mapper.FxCustomerAccountMapper;
import com.xfunds.mapper.FxCustomerMapper;
import com.xfunds.mapper.FxQuoteMapper;
import com.xfunds.mapper.FxTradeMasterMapper;
import com.xfunds.service.ProductRecommendService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 客户产品推荐服务实现
 * 系统实际支持的产品：即期(SPOT)、远期(FORWARD)、掉期(SWAP)、美式期权(AMERICAN_OPTION)、欧式期权(EUROPEAN_OPTION)
 * 采用"实时画像计算 + 规则引擎匹配"架构，依据 RFM/AUM/KYC/敞口四大维度推荐产品
 */
@Service
public class ProductRecommendServiceImpl implements ProductRecommendService {

    private static final Logger log = LoggerFactory.getLogger(ProductRecommendServiceImpl.class);

    @Autowired
    private FxCustomerMapper fxCustomerMapper;
    @Autowired
    private FxCustomerAccountMapper fxCustomerAccountMapper;
    @Autowired
    private FxTradeMasterMapper fxTradeMasterMapper;
    @Autowired
    private FxQuoteMapper fxQuoteMapper;

    // ========== 系统支持的产品编码 ==========
    private static final String SPOT = "SPOT";
    private static final String FORWARD = "FORWARD";
    private static final String SWAP = "SWAP";
    private static final String AMERICAN_OPTION = "AMERICAN_OPTION";
    private static final String EUROPEAN_OPTION = "EUROPEAN_OPTION";

    // ========== 产品名称 ==========
    private static final Map<String, String> PRODUCT_NAMES = new LinkedHashMap<>();
    static {
        PRODUCT_NAMES.put(SPOT, "即期外汇买卖");
        PRODUCT_NAMES.put(FORWARD, "远期外汇买卖");
        PRODUCT_NAMES.put(SWAP, "外汇掉期");
        PRODUCT_NAMES.put(AMERICAN_OPTION, "美式期权");
        PRODUCT_NAMES.put(EUROPEAN_OPTION, "欧式期权");
    }

    // ========== 产品风险层级 ==========
    private static final Map<String, String> PRODUCT_RISK_TIER = new HashMap<>();
    static {
        PRODUCT_RISK_TIER.put(SPOT, "BASIC");
        PRODUCT_RISK_TIER.put(FORWARD, "STANDARD");
        PRODUCT_RISK_TIER.put(SWAP, "STANDARD");
        PRODUCT_RISK_TIER.put(AMERICAN_OPTION, "COMPLEX");
        PRODUCT_RISK_TIER.put(EUROPEAN_OPTION, "COMPLEX");
    }

    @Override
    public ProductRecommendResponse recommendProducts(String customerId) {
        ProductRecommendResponse response = new ProductRecommendResponse();
        response.setCustomerId(customerId);

        // 1. 加载客户基础信息
        FxCustomer customer = fxCustomerMapper.selectByCustomerId(customerId);
        if (customer == null) {
            throw new IllegalArgumentException("客户不存在：" + customerId);
        }
        response.setCustomerName(customer.getCustomerName());

        // 2. 构建客户画像
        CustomerPortraitDTO portrait = buildPortrait(customer);
        response.setPortrait(portrait);
        log.info("[产品推荐] 客户={} 画像：RFM={} AUM={} 风险={} 衍生品资质={} 敞口={}",
                customerId, portrait.getRfmSegment(), portrait.getAumLabel(),
                portrait.getRiskLevel(), portrait.getHasDerivativeLicense(), portrait.getExposureType());

        // 3. 合规硬过滤：确定可用产品池
        Set<String> available = new LinkedHashSet<>(PRODUCT_NAMES.keySet());
        applyHardFilters(portrait, available);
        log.info("[产品推荐] 客户={} 合规过滤后可用产品：{}", customerId, available);

        // 4. 规则匹配：按场景优先级推荐
        List<ProductRecommendDTO> recommendations = matchRules(portrait, available);

        // 4.5 结合汇率趋势丰富推荐理由
        enrichWithRateTrend(portrait, recommendations);

        // 5. 兜底：无推荐结果时推荐即期
        boolean fallback = false;
        if (recommendations.isEmpty()) {
            fallback = true;
            if (available.contains(SPOT)) {
                recommendations.add(buildRecommend(SPOT, 1, "兜底推荐",
                        "根据您当前的情况，建议从即期外汇买卖开始，灵活应对汇率波动。"));
            }
            if (available.contains(FORWARD)) {
                recommendations.add(buildRecommend(FORWARD, 2, "兜底推荐",
                        "若有未来确定的收付汇需求，远期可提前锁定汇率成本，规避波动风险。"));
            }
            // 兜底也追加汇率趋势
            enrichWithRateTrend(portrait, recommendations);
        }

        // 6. 取 Top 3
        if (recommendations.size() > 3) {
            recommendations = recommendations.subList(0, 3);
        }

        response.setRecommendations(recommendations);
        response.setFallback(fallback);
        return response;
    }

    // ==================== 画像构建 ====================

    private CustomerPortraitDTO buildPortrait(FxCustomer customer) {
        CustomerPortraitDTO p = new CustomerPortraitDTO();
        p.setCustomerId(customer.getCustomerId());
        p.setCustomerName(customer.getCustomerName());
        p.setCustomerType(customer.getCustomerType());
        p.setCorpType(customer.getCorpType());
        p.setCreditLevel(customer.getCreditLevel());
        p.setRiskLevel(mapRiskLevel(customer.getRiskLevel()));
        p.setTagUpdateTime(LocalDateTime.now().toString());

        // RFM 计算
        Map<String, Object> rfm = fxTradeMasterMapper.selectRfmByCustomerId(customer.getCustomerId());
        int recencyDays = rfm != null && rfm.get("recency_days") != null
                ? ((Number) rfm.get("recency_days")).intValue() : 999;
        int frequency = rfm != null && rfm.get("frequency_12m") != null
                ? ((Number) rfm.get("frequency_12m")).intValue() : 0;
        BigDecimal monetary = rfm != null && rfm.get("monetary_12m_cny") != null
                ? new BigDecimal(rfm.get("monetary_12m_cny").toString()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        p.setRecencyDays(recencyDays);
        p.setFrequency12m(frequency);
        p.setMonetary12mCny(monetary);
        p.setRLabel(calcRLabel(recencyDays));
        p.setFLabel(calcFLabel(frequency));
        p.setMLabel(calcMLabel(monetary));
        p.setRfmSegment(calcRfmSegment(recencyDays, frequency, monetary));

        // AUM 计算（SQL 直接汇总启用账户的折人民币余额，避免将全部账户加载到内存）
        BigDecimal aum = fxCustomerAccountMapper.sumCnyBalanceByCustomerId(customer.getCustomerId());
        if (aum == null) {
            aum = BigDecimal.ZERO;
        }
        aum = aum.setScale(2, RoundingMode.HALF_UP);
        p.setAumTotalCny(aum);
        p.setAumLabel(calcAumLabel(aum));

        // 衍生品资质、行业、敞口推断（基于历史交易）
        List<FxTradeMaster> trades = fxTradeMasterMapper.selectValidByCustomerId(customer.getCustomerId());
        p.setHasDerivativeLicense(inferDerivativeLicense(trades, customer));
        p.setIndustry(inferIndustry(customer.getCustomerName()));
        p.setExposureType(inferExposureType(trades));

        // 汇率趋势分析（USD/CNY 近7天报价汇率序列）
        analyzeRateTrend(p);

        return p;
    }

    /**
     * 分析 USD/CNY 近7天汇率趋势
     * 数据源：fx_quote 表 SPOT 类型报价的 market_mid_rate，按 effective_time 升序
     * 通过首尾汇率计算涨跌幅，判断美元升值/贬值/震荡
     */
    private void analyzeRateTrend(CustomerPortraitDTO p) {
        List<FxQuote> quoteList = fxQuoteMapper.selectRecentRates("SPOT", "USD/CNY", 7);
        if (quoteList == null || quoteList.size() < 2) {
            p.setRateTrend("UNKNOWN");
            p.setRateTrendLabel("汇率数据不足，暂无法判断趋势");
            return;
        }

        // 首尾市场中间价
        BigDecimal firstRate = quoteList.get(0).getMarketMidRate();
        BigDecimal lastRate = quoteList.get(quoteList.size() - 1).getMarketMidRate();
        // 涨跌幅 = (最新 - 最早) / 最早 * 100
        BigDecimal changePct = lastRate.subtract(firstRate)
                .divide(firstRate, 6, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);

        p.setCurrentRate(lastRate);
        p.setRateChangePct(changePct);

        // 趋势判定：涨跌幅绝对值 > 0.3% 视为方向性趋势
        String trend;
        String trendDesc;
        if (changePct.compareTo(new BigDecimal("0.3")) > 0) {
            trend = "USD_UP";
            trendDesc = String.format("美元升值（人民币贬值），近7天 USD/CNY 从 %.4f 升至 %.4f，涨幅 %.2f%%",
                    firstRate, lastRate, changePct);
        } else if (changePct.compareTo(new BigDecimal("-0.3")) < 0) {
            trend = "USD_DOWN";
            trendDesc = String.format("美元贬值（人民币升值），近7天 USD/CNY 从 %.4f 跌至 %.4f，跌幅 %.2f%%",
                    firstRate, lastRate, changePct);
        } else {
            trend = "FLAT";
            trendDesc = String.format("汇率窄幅震荡，近7天 USD/CNY 在 %.4f ~ %.4f 区间波动，变化 %.2f%%",
                    firstRate, lastRate, changePct);
        }
        p.setRateTrend(trend);
        p.setRateTrendLabel(trendDesc);

        log.info("[产品推荐] 汇率趋势：{} 当前={} 涨跌幅={}%", trend, lastRate, changePct);
    }

    // ==================== 合规硬过滤 ====================

    private void applyHardFilters(CustomerPortraitDTO p, Set<String> available) {
        // 1. 金融机构类客户不进入代客推荐流程（V2 文档第四节）
        if ("FINANCIAL_INSTITUTION".equals(p.getCorpType())) {
            available.clear();
            return;
        }
        // 2. KYC 风险等级过滤：保守型客户剔除期权类产品
        if ("CONSERVATIVE".equals(p.getRiskLevel())) {
            available.removeAll(Arrays.asList(AMERICAN_OPTION, EUROPEAN_OPTION));
        }
        // 3. 衍生品适当性认证过滤：未通过测评剔除远期、掉期、期权
        if (!Boolean.TRUE.equals(p.getHasDerivativeLicense())) {
            available.removeAll(Arrays.asList(FORWARD, SWAP, AMERICAN_OPTION, EUROPEAN_OPTION));
        }
        // 4. AUM 覆盖过滤：低资产客户剔除期权（需支付期权费）
        if (p.getAumTotalCny() != null && p.getAumTotalCny().compareTo(new BigDecimal("700000")) < 0) {
            available.removeAll(Arrays.asList(AMERICAN_OPTION, EUROPEAN_OPTION));
        }
        // 5. 客户类型过滤：个人客户剔除掉期（仅对公产品）
        if ("RETAIL".equals(p.getCustomerType())) {
            available.remove(SWAP);
        }
    }

    // ==================== 规则匹配 ====================

    private List<ProductRecommendDTO> matchRules(CustomerPortraitDTO p, Set<String> available) {
        List<ProductRecommendDTO> result = new ArrayList<>();

        // ========== 场景一：高价值活跃套保型企业 ==========
        // V2 5.1：RFM高（R<30天，F>10笔，M>1000万元）+ AUM充足 + 平衡/激进 + 有贸易敞口
        if ("CORP".equals(p.getCustomerType())
                && p.getRecencyDays() != null && p.getRecencyDays() <= 30
                && p.getFrequency12m() != null && p.getFrequency12m() > 10
                && p.getMonetary12mCny() != null && p.getMonetary12mCny().compareTo(new BigDecimal("10000000")) >= 0
                && !"NONE".equals(p.getExposureType())
                && !"CONSERVATIVE".equals(p.getRiskLevel())) {

            String scenario = "高价值活跃套保型企业";
            // 远期：有敞口预测，锁定对应期限汇率
            if (available.contains(FORWARD)) {
                result.add(buildRecommend(FORWARD, 1, scenario,
                        String.format("贵司近12个月交易%d笔、总额%.2f万元，属于高价值活跃客户。结合%s敞口，建议使用远期锁定汇率成本，规避波动风险。",
                                p.getFrequency12m(), p.getMonetary12mCny().divide(new BigDecimal("10000"), 2, RoundingMode.HALF_UP),
                                exposureText(p.getExposureType()))));
            }
            // 掉期：有短期外币贷款还款压力或需调整外币存款期限结构
            if (available.contains(SWAP)) {
                result.add(buildRecommend(SWAP, 2, scenario,
                        "作为高价值企业客户，若有账期错配（如收到外币货款但后续才需支付），可通过掉期交易匹配资金期限，提升资金使用效率。"));
            }
            // 期权：锁定风险同时保留收益空间
            if (available.contains(EUROPEAN_OPTION) && result.size() < 3) {
                result.add(buildRecommend(EUROPEAN_OPTION, 3, scenario,
                        "若您希望锁定风险的同时保留汇率有利变动的收益空间，欧式期权可在到期日行权，适合有明确到期日敞口的对冲需求。"));
            }
            if (available.contains(AMERICAN_OPTION) && result.size() < 3) {
                result.add(buildRecommend(AMERICAN_OPTION, 4, scenario,
                        "美式期权允许在到期前任意交易日行权，灵活性更高，适合敞口时间不确定或希望随时锁定收益的企业。"));
            }
            return result;
        }

        // ========== 场景三：制造业/贸易业企业 ==========
        // V2 5.3：企业类型为 MANUFACTURING 或 TRADING，有持续双向现金流
        String ct = p.getCorpType();
        if ("CORP".equals(p.getCustomerType()) && ("MANUFACTURING".equals(ct) || "TRADING".equals(ct))) {
            String scenario = "制造业/贸易业企业";
            String bizName = "MANUFACTURING".equals(ct) ? "制造" : "贸易";

            // 远期：制造业有明确单笔大额应收/应付敞口，使用标准远期锁定；贸易业也需套保
            if (available.contains(FORWARD)) {
                result.add(buildRecommend(FORWARD, 1, scenario,
                        String.format("贵司属于%s业企业，有持续收付汇需求。远期可提前锁定汇率成本，是最基础的套保工具。", bizName)));
            }
            // 贸易业：美式期权（资金周转快，需提前行权灵活性）
            if ("TRADING".equals(ct) && available.contains(AMERICAN_OPTION) && result.size() < 3) {
                result.add(buildRecommend(AMERICAN_OPTION, 2, scenario,
                        "贸易业客户资金周转快，需要提前行权的灵活性。美式期权支付期权费即可获得汇率保护，若汇率向有利方向波动仍可全额受益。"));
            }
            // 贸易业：欧式期权（对汇率有明确判断，到期日行权）
            if ("TRADING".equals(ct) && available.contains(EUROPEAN_OPTION) && result.size() < 3) {
                result.add(buildRecommend(EUROPEAN_OPTION, 3, scenario,
                        "若您对汇率走势有明确判断，认为当前价位已足以提供保护，欧式期权支付期权费可在到期日获得结汇优势，成本明确且可控。"));
            }
            // 掉期：管理账期错配（收到外币货款但后续才需支付）
            if (available.contains(SWAP) && result.size() < 3) {
                result.add(buildRecommend(SWAP, 4, scenario,
                        "管理账期错配：如收到外币货款但后续才需支付，可通过掉期交易匹配资金期限，提升资金效率。"));
            }
            // 制造业补充即期（若未满3条）
            if (result.size() < 3 && available.contains(SPOT)) {
                result.add(buildRecommend(SPOT, 5, scenario,
                        "即期外汇买卖可满足日常即时换汇需求，灵活便捷。"));
            }
            if (!result.isEmpty()) return result;
        }

        // ========== 场景二：中低频/稳健型企业或个人高净值 ==========
        // V2 5.2：RFM中等（F较低，M中等）+ AUM高 + 保守/平衡
        if (p.getFrequency12m() != null && p.getFrequency12m() < 5
                && p.getAumTotalCny() != null && p.getAumTotalCny().compareTo(new BigDecimal("700000")) >= 0) {
            String scenario = "中低频/稳健型";
            // 即期：有即时支付需求或对汇率走势不确定选择观望
            if (available.contains(SPOT)) {
                result.add(buildRecommend(SPOT, 1, scenario,
                        "您交易频次较低但资产规模充足，即期外汇买卖灵活便捷，适合当前有即时支付需求或对汇率走势不确定时观望操作。"));
            }
            // 远期：希望锁定未来汇率
            if (available.contains(FORWARD)) {
                result.add(buildRecommend(FORWARD, 2, scenario,
                        "若有未来确定的收付汇需求，远期可提前锁定汇率成本，避免期间汇率波动带来的不确定性。"));
            }
            // 期权（非保守型）：担心汇率大幅波动，支付期权费买保险
            if (!"CONSERVATIVE".equals(p.getRiskLevel())) {
                if (available.contains(AMERICAN_OPTION) && result.size() < 3) {
                    result.add(buildRecommend(AMERICAN_OPTION, 3, scenario,
                            "若您担心汇率大幅波动但不想占用全额本金做远期，可支付少量期权费买入美式期权，获得汇率保险且可随时行权。"));
                }
                if (available.contains(EUROPEAN_OPTION) && result.size() < 3) {
                    result.add(buildRecommend(EUROPEAN_OPTION, 4, scenario,
                            "欧式期权成本明确且可控，支付期权费可在到期日获得结汇优势。"));
                }
            }
            return result;
        }

        // ========== 场景四：休眠唤醒/低价值客户 ==========
        // V2 5.4：RFM低（R>180天）+ AUM低 + 风险未知/保守
        if (p.getRecencyDays() != null && p.getRecencyDays() > 180) {
            String scenario = "休眠唤醒/低价值客户";
            if (available.contains(SPOT)) {
                result.add(buildRecommend(SPOT, 1, scenario,
                        "您已有较长时间未进行外汇交易，建议从即期换汇开始，灵活应对当前汇率波动。"));
            }
            return result;
        }

        // ========== 通用推荐：有交易但未命中特定场景 ==========
        if (result.isEmpty() && p.getFrequency12m() != null && p.getFrequency12m() > 0) {
            if (available.contains(SPOT)) {
                result.add(buildRecommend(SPOT, 1, "通用推荐",
                        "根据您的交易记录，即期外汇买卖可满足日常换汇需求。"));
            }
            if (available.contains(FORWARD)) {
                result.add(buildRecommend(FORWARD, 2, "通用推荐",
                        "若有未来确定的收付汇需求，远期可提前锁定汇率成本。"));
            }
            if (available.contains(SWAP)) {
                result.add(buildRecommend(SWAP, 3, "通用推荐",
                        "若存在资金期限错配，掉期可同时完成即期和远期交易，匹配资金流。"));
            }
        }

        return result;
    }

    // ==================== 辅助方法 ====================

    private ProductRecommendDTO buildRecommend(String productCode, int priority, String scenario, String reason) {
        ProductRecommendDTO dto = new ProductRecommendDTO();
        dto.setProductCode(productCode);
        dto.setProductName(PRODUCT_NAMES.get(productCode));
        dto.setPriority(priority);
        dto.setScenario(scenario);
        dto.setReason(reason);
        dto.setRiskTier(PRODUCT_RISK_TIER.get(productCode));
        return dto;
    }

    /**
     * 结合汇率趋势丰富推荐理由
     * 根据产品类型、客户敞口方向、汇率趋势，追加针对性的汇率分析建议
     */
    private void enrichWithRateTrend(CustomerPortraitDTO p, List<ProductRecommendDTO> recs) {
        if (recs == null || recs.isEmpty() || "UNKNOWN".equals(p.getRateTrend())) {
            return;
        }
        String trend = p.getRateTrend();
        String exposure = p.getExposureType();
        String trendBrief = p.getRateTrendLabel() != null ? p.getRateTrendLabel() : "";

        for (ProductRecommendDTO rec : recs) {
            String product = rec.getProductCode();
            String rateAdvice = buildRateAdvice(product, exposure, trend);
            if (rateAdvice != null && !rateAdvice.isEmpty()) {
                rec.setReason(rec.getReason() + "\n【汇率参考】" + trendBrief + "。" + rateAdvice);
            }
        }
    }

    /**
     * 根据产品类型、敞口方向、汇率趋势生成针对性建议
     */
    private String buildRateAdvice(String product, String exposure, String trend) {
        // 应付（购汇）客户：美元升值不利，美元贬值有利
        // 应收（结汇）客户：美元升值有利，美元贬值不利
        boolean hasPayable = "PAYABLE".equals(exposure) || "DUAL".equals(exposure);
        boolean hasReceivable = "RECEIVABLE".equals(exposure) || "DUAL".equals(exposure);

        switch (product) {
            case FORWARD:
                // 远期：锁定未来某一时点的单方向收付汇汇率
                if ("USD_UP".equals(trend)) {
                    if (hasPayable) {
                        return "美元处于升值通道，您有购汇需求，建议尽快通过远期锁定当前汇率，避免进一步升值增加购汇成本。";
                    }
                    if (hasReceivable) {
                        return "美元处于升值通道，您有结汇需求，可暂缓结汇或观望，待汇率进一步走强后结汇更划算。";
                    }
                    return "美元处于升值通道，若有购汇需求建议尽快通过远期锁定汇率。";
                }
                if ("USD_DOWN".equals(trend)) {
                    if (hasReceivable) {
                        return "美元处于贬值通道，您有结汇需求，建议尽快通过远期锁定当前汇率，避免进一步贬值减少结汇收入。";
                    }
                    if (hasPayable) {
                        return "美元处于贬值通道，您有购汇需求，可暂缓购汇或观望，待汇率进一步走弱后购汇更划算。";
                    }
                    return "美元处于贬值通道，若有结汇需求建议尽快通过远期锁定汇率。";
                }
                // FLAT
                return "汇率窄幅震荡，远期可锁定未来某一时点的收付汇汇率成本，规避期间突发波动。";

            case SWAP:
                // 掉期：同时做即期+反向远期，核心是资金期限错配管理，汇率趋势影响掉期点成本
                if ("USD_UP".equals(trend)) {
                    return "美元处于升值通道（远期升水），掉期远端卖出美元可获得更高远期汇率。"
                            + (hasReceivable ? "若您即期收到外币、远期才需结汇，可通过掉期匹配资金期限，同时在远端锁定较高的结汇汇率。"
                            : hasPayable ? "若您即期需购汇、远期有外币收入回笼，掉期可匹配资金流，但远端购汇成本可能因升水而略增，建议综合评估。"
                            : "适合管理即期与远期资金期限错配，远端结汇方向更有利。");
                }
                if ("USD_DOWN".equals(trend)) {
                    return "美元处于贬值通道（远期贴水），掉期远端买入美元成本更低。"
                            + (hasPayable ? "若您即期卖出外币、远期才需购汇支付，可通过掉期匹配资金期限，同时在远端锁定较低的购汇成本。"
                            : hasReceivable ? "若您即期收到外币、远期才需结汇，掉期可匹配资金流，但远端结汇汇率可能因贴水而略减，建议综合评估。"
                            : "适合管理即期与远期资金期限错配，远端购汇方向更有利。");
                }
                // FLAT
                return "汇率窄幅震荡，掉期点稳定，适合管理资金期限错配（如即期收外币、远期付外币），锁定同时段资金成本。";

            case SPOT:
                if ("USD_UP".equals(trend) && hasReceivable) {
                    return "美元处于升值通道，当前结汇可获得较高人民币收入，若有即期结汇需求是较好时机。";
                }
                if ("USD_DOWN".equals(trend) && hasPayable) {
                    return "美元处于贬值通道，当前购汇成本较低，若有即期购汇需求是较好时机。";
                }
                return "当前汇率波动较小，即期操作可灵活应对即时收付汇需求。";
            case AMERICAN_OPTION:
            case EUROPEAN_OPTION:
                if ("FLAT".equals(trend)) {
                    return "汇率震荡格局下，期权可支付少量期权费获得汇率保护，同时保留汇率向有利方向波动时的收益空间。";
                }
                return "无论汇率向哪个方向波动，期权都能锁定风险同时保留收益空间，适合当前波动环境。";
            default:
                return null;
        }
    }

    /** 风评等级码值映射：A→激进型，B→平衡型，C→保守型，空值默认保守型 */
    private String mapRiskLevel(String riskLevel) {
        if (riskLevel == null || riskLevel.isEmpty()) {
            return "CONSERVATIVE";
        }
        switch (riskLevel.toUpperCase()) {
            case "A": return "AGGRESSIVE";
            case "B": return "BALANCED";
            case "C": return "CONSERVATIVE";
            default: return "CONSERVATIVE";
        }
    }

    private String calcRLabel(int recencyDays) {
        if (recencyDays <= 7) return "R1-高活跃";
        if (recencyDays <= 30) return "R2-活跃";
        if (recencyDays <= 90) return "R3-一般";
        if (recencyDays <= 180) return "R4-沉睡";
        return "R5-流失";
    }

    private String calcFLabel(int frequency) {
        if (frequency >= 50) return "F1-高频";
        if (frequency >= 20) return "F2-中频";
        if (frequency >= 5) return "F3-低频";
        return "F4-极少";
    }

    private String calcMLabel(BigDecimal monetary) {
        if (monetary.compareTo(new BigDecimal("35000000")) >= 0) return "M1-超高净值";
        if (monetary.compareTo(new BigDecimal("7000000")) >= 0) return "M2-高净值";
        if (monetary.compareTo(new BigDecimal("700000")) >= 0) return "M3-中资产";
        return "M4-低资产";
    }

    private String calcRfmSegment(int recencyDays, int frequency, BigDecimal monetary) {
        String r = recencyDays <= 30 ? "A" : recencyDays <= 90 ? "B" : "C";
        String f = frequency >= 20 ? "1" : frequency >= 5 ? "2" : "3";
        String m = monetary.compareTo(new BigDecimal("7000000")) >= 0 ? "1"
                : monetary.compareTo(new BigDecimal("700000")) >= 0 ? "2" : "3";
        return r + f + m;
    }

    private String calcAumLabel(BigDecimal aum) {
        if (aum.compareTo(new BigDecimal("35000000")) >= 0) return "AUM1-超高净值";
        if (aum.compareTo(new BigDecimal("7000000")) >= 0) return "AUM2-高净值";
        if (aum.compareTo(new BigDecimal("700000")) >= 0) return "AUM3-中资产";
        return "AUM4-低资产";
    }

    /** 推断衍生品资质：有远期/掉期/期权有效交易记录，或企业客户且风评等级为激进型/平衡型 */
    private Boolean inferDerivativeLicense(List<FxTradeMaster> trades, FxCustomer customer) {
        if (trades != null) {
            for (FxTradeMaster t : trades) {
                String type = t.getTradeType();
                if ("FORWARD".equals(type) || "SWAP".equals(type) || "OPTION".equals(type)) {
                    return true;
                }
            }
        }
        // 企业客户且风评等级非保守型默认有资质
        if ("CORP".equals(customer.getCustomerType())) {
            String rl = mapRiskLevel(customer.getRiskLevel());
            return "AGGRESSIVE".equals(rl) || "BALANCED".equals(rl);
        }
        return false;
    }

    /** 推断行业：基于客户名称关键词 */
    private String inferIndustry(String name) {
        if (name == null) return "Unknown";
        if (name.contains("制造") || name.contains("材料") || name.contains("钢铁") || name.contains("石油")
                || name.contains("物业") || name.contains("集团")) {
            return "Manufacturing";
        }
        if (name.contains("贸易") || name.contains("商务") || name.contains("进出口")) {
            return "Trading";
        }
        if (name.contains("科技") || name.contains("电子") || name.contains("信息")) {
            return "Technology";
        }
        return "Other";
    }

    /** 推断敞口类型：统计 BUY/SELL 方向 */
    private String inferExposureType(List<FxTradeMaster> trades) {
        if (trades == null || trades.isEmpty()) return "NONE";
        int buy = 0, sell = 0;
        for (FxTradeMaster t : trades) {
            if ("BUY".equals(t.getTradeDirection())) buy++;
            else if ("SELL".equals(t.getTradeDirection())) sell++;
        }
        if (buy > 0 && sell > 0) return "DUAL";
        if (buy > sell) return "PAYABLE";
        if (sell > buy) return "RECEIVABLE";
        return "NONE";
    }

    private String exposureText(String exposureType) {
        switch (exposureType) {
            case "PAYABLE": return "应付（购汇）";
            case "RECEIVABLE": return "应收（结汇）";
            case "DUAL": return "双向收付汇";
            default: return "";
        }
    }
}
