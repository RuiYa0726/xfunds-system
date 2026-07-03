package com.xfunds.service.impl;

import com.xfunds.common.BusinessException;
import com.xfunds.common.Constants;
import com.xfunds.common.ResultCode;
import com.xfunds.common.SecurityUtils;
import com.xfunds.dto.ForwardTradeEntryRequest;
import com.xfunds.dto.PageResponse;
import com.xfunds.dto.SpotTradeEntryRequest;
import com.xfunds.dto.SwapTradeEntryRequest;
import com.xfunds.dto.TradeDetailVO;
import com.xfunds.dto.TradeResponse;
import com.xfunds.entity.FxCustomer;
import com.xfunds.entity.FxForwardTrade;
import com.xfunds.entity.FxMarginAccount;
import com.xfunds.entity.FxOrg;
import com.xfunds.entity.FxSpotTrade;
import com.xfunds.entity.FxSwapTrade;
import com.xfunds.entity.FxTradeMaster;
import com.xfunds.entity.FxUser;
import com.xfunds.enums.RoleCode;
import com.xfunds.enums.SettlementMethod;
import com.xfunds.enums.SpecialTradeType;
import com.xfunds.enums.TaskType;
import com.xfunds.enums.TradeStatus;
import com.xfunds.enums.TradeType;
import com.xfunds.mapper.FxCustomerMapper;
import com.xfunds.mapper.FxForwardTradeMapper;
import com.xfunds.mapper.FxMarginAccountMapper;
import com.xfunds.mapper.FxOrgMapper;
import com.xfunds.mapper.FxSpotTradeMapper;
import com.xfunds.mapper.FxSwapTradeMapper;
import com.xfunds.mapper.FxTradeMasterMapper;
import com.xfunds.mapper.FxUserMapper;
import com.xfunds.service.ApprovalLogService;
import com.xfunds.service.FxTradeService;
import com.xfunds.service.TaskService;
import com.xfunds.service.TradeLifecycleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 交易服务实现类
 */
@Service
public class FxTradeServiceImpl implements FxTradeService {

    /** 交易ID时间格式：yyyyMMddHHmmss */
    private static final DateTimeFormatter TRADE_ID_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 业务编号日期格式：yyyyMMdd */
    private static final DateTimeFormatter BIZ_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 保证金比例：交易金额的 10% */
    private static final BigDecimal MARGIN_RATE = new BigDecimal("0.10");

    @Autowired
    private FxTradeMasterMapper fxTradeMasterMapper;

    @Autowired
    private FxSpotTradeMapper fxSpotTradeMapper;

    @Autowired
    private FxForwardTradeMapper fxForwardTradeMapper;

    @Autowired
    private FxSwapTradeMapper fxSwapTradeMapper;

    @Autowired
    private FxCustomerMapper fxCustomerMapper;

    @Autowired
    private FxOrgMapper fxOrgMapper;

    @Autowired
    private FxUserMapper fxUserMapper;

    @Autowired
    private FxMarginAccountMapper fxMarginAccountMapper;

    @Autowired
    private TradeLifecycleService tradeLifecycleService;

    @Autowired
    private ApprovalLogService approvalLogService;

    @Autowired
    private TaskService taskService;

    /**
     * 根据交易ID查询交易
     */
    @Override
    public FxTradeMaster getByTradeId(String tradeId) {
        return fxTradeMasterMapper.selectByTradeId(tradeId);
    }

    /**
     * 查询所有交易
     */
    @Override
    public List<FxTradeMaster> listAll() {
        return fxTradeMasterMapper.selectAll();
    }

    /**
     * 校验保证金账户余额并服务端重算保证金金额
     * 保证金 = 交易金额 × 10%，前端不可人工修改，后端强制重算防止篡改
     * 若保证金账户可用余额（余额 - 冻结金额）不足，抛出业务异常阻止交易发起
     *
     * @param marginAccountId 保证金账户ID（可为空，为空则不校验）
     * @param tradeAmount     交易金额（用于计算保证金）
     * @return 服务端重算后的保证金金额
     */
    private BigDecimal validateAndCalcMargin(String marginAccountId, BigDecimal tradeAmount) {
        // 服务端强制重算保证金 = 交易金额 × 10%
        BigDecimal marginAmount = tradeAmount.multiply(MARGIN_RATE)
                .setScale(2, RoundingMode.HALF_UP);

        if (marginAccountId != null && !marginAccountId.isEmpty()) {
            FxMarginAccount marginAccount = fxMarginAccountMapper.selectByMarginAccountId(marginAccountId);
            if (marginAccount == null) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "保证金账户不存在：" + marginAccountId);
            }
            BigDecimal balance = marginAccount.getBalance() != null ? marginAccount.getBalance() : BigDecimal.ZERO;
            BigDecimal frozen = marginAccount.getFrozenAmount() != null ? marginAccount.getFrozenAmount() : BigDecimal.ZERO;
            BigDecimal available = balance.subtract(frozen);
            if (available.compareTo(marginAmount) < 0) {
                throw new BusinessException(ResultCode.PARAM_ERROR,
                        "保证金账户余额不足：可用 " + available + "，需要保证金 " + marginAmount);
            }
        }
        return marginAmount;
    }

    /**
     * 创建即期交易：生成主表与即期子表，记录经办生命周期事件，
     * 随后自动提交复核（DRAFT->PENDING_CHECK）并创建复核任务
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createSpotTrade(SpotTradeEntryRequest req) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String currentOrgCode = SecurityUtils.getCurrentOrgCode();

        // 生成交易ID与业务编号
        String tradeId = generateTradeId(Constants.PREFIX_SPOT);
        String businessNo = generateBusinessNo(Constants.PREFIX_SPOT);

        // 查询客户名称
        String customerName = lookupCustomerName(req.getCustomerId());

        // 计算对手金额 = 名义金额 × 客户汇率
        BigDecimal counterAmount = req.getNotionalAmount()
                .multiply(req.getCustomerRate())
                .setScale(2, RoundingMode.HALF_UP);

        // 校验保证金账户余额并服务端重算保证金金额（交易金额 × 10%）
        BigDecimal marginAmount = validateAndCalcMargin(req.getMarginAccountId(), req.getNotionalAmount());

        // 构建交易主表
        FxTradeMaster master = new FxTradeMaster();
        master.setTradeId(tradeId);
        master.setBusinessNo(businessNo);
        master.setTradeType(TradeType.SPOT.name());
        master.setStatus(TradeStatus.DRAFT.name());
        master.setBranchCode(req.getBranchCode() != null ? req.getBranchCode() : currentOrgCode);
        master.setCustomerId(req.getCustomerId());
        master.setCustomerName(customerName);
        master.setBaseCurrency(req.getBaseCurrency());
        master.setQuoteCurrency(req.getQuoteCurrency());
        master.setCurrencyPair(req.getBaseCurrency() + "/" + req.getQuoteCurrency());
        master.setNotionalAmount(req.getNotionalAmount());
        master.setCounterAmount(counterAmount);
        master.setTradeDirection(req.getTradeDirection());
        master.setValueDate(req.getValueDate());
        master.setTradeDate(req.getTradeDate());
        master.setMaturityDate(req.getTradeDate());
        master.setDeliveryType(req.getDeliveryType());
        master.setSettlementMethod(SettlementMethod.FULL.name());
        master.setSpotRate(req.getSpotRate());
        master.setCustomerRate(req.getCustomerRate());
        master.setCostRate(req.getCostRate());
        master.setBranchProfitPoint(req.getBranchProfitPoint());
        master.setSpecialTradeType(SpecialTradeType.NORMAL.name());
        master.setMakerId(currentUserId);
        master.setMakeTime(LocalDateTime.now());
        master.setVersion(Constants.DEFAULT_VERSION);
        master.setPurposeCode(req.getPurposeCode());
        master.setFxPurposeCode(req.getFxPurposeCode());
        fxTradeMasterMapper.insert(master);

        // 构建即期子表
        FxSpotTrade spotTrade = new FxSpotTrade();
        spotTrade.setTradeId(tradeId);
        spotTrade.setSettlementType(req.getDeliveryType());
        spotTrade.setSpotRate(req.getSpotRate());
        spotTrade.setCustomerRate(req.getCustomerRate());
        spotTrade.setCostRate(req.getCostRate());
        spotTrade.setCurrency1Account(req.getCurrency1Account());
        spotTrade.setCurrency2Account(req.getCurrency2Account());
        spotTrade.setMarginAccountId(req.getMarginAccountId());
        spotTrade.setMarginAmount(marginAmount);
        spotTrade.setAmount(req.getNotionalAmount());
        fxSpotTradeMapper.insert(spotTrade);

        // 记录经办生命周期事件：null -> DRAFT
        tradeLifecycleService.recordEvent(tradeId, Constants.NODE_MAKE, currentUserId,
                null, TradeStatus.DRAFT.name(), req.getNotionalAmount(), req.getCustomerRate(),
                null, "即期交易经办录入");

        // 自动提交复核：DRAFT -> PENDING_CHECK
        submitToCheck(master, currentUserId, currentOrgCode);

        return tradeId;
    }

    /**
     * 创建远期交易：生成主表与远期子表，记录经办生命周期事件，
     * 随后自动提交复核（DRAFT->PENDING_CHECK）并创建复核任务
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createForwardTrade(ForwardTradeEntryRequest req) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String currentOrgCode = SecurityUtils.getCurrentOrgCode();

        // 生成交易ID与业务编号
        String tradeId = generateTradeId(Constants.PREFIX_FORWARD);
        String businessNo = generateBusinessNo(Constants.PREFIX_FORWARD);

        // 查询客户名称
        String customerName = lookupCustomerName(req.getCustomerId());

        // 计算对手金额 = 名义金额 × 客户汇率（远期交易以客户汇率作为实际成交汇率）
        BigDecimal customerRate = req.getCustomerRate();
        BigDecimal counterAmount = req.getNotionalAmount()
                .multiply(customerRate)
                .setScale(2, RoundingMode.HALF_UP);

        // 校验保证金账户余额并服务端重算保证金金额（交易金额 × 10%）
        BigDecimal marginAmount = validateAndCalcMargin(req.getMarginAccountId(), req.getNotionalAmount());

        // 构建交易主表
        FxTradeMaster master = new FxTradeMaster();
        master.setTradeId(tradeId);
        master.setBusinessNo(businessNo);
        master.setTradeType(TradeType.FORWARD.name());
        master.setStatus(TradeStatus.DRAFT.name());
        master.setBranchCode(req.getBranchCode() != null ? req.getBranchCode() : currentOrgCode);
        master.setCustomerId(req.getCustomerId());
        master.setCustomerName(customerName);
        master.setBaseCurrency(req.getBaseCurrency());
        master.setQuoteCurrency(req.getQuoteCurrency());
        master.setCurrencyPair(req.getBaseCurrency() + "/" + req.getQuoteCurrency());
        master.setNotionalAmount(req.getNotionalAmount());
        master.setCounterAmount(counterAmount);
        master.setTradeDirection(req.getTradeDirection());
        // 远期交易起息日取交易日，到期日取 maturityDate
        master.setValueDate(req.getTradeDate());
        master.setTradeDate(req.getTradeDate());
        master.setMaturityDate(req.getMaturityDate());
        master.setDeliveryType(req.getDeliveryType());
        master.setSettlementMethod(req.getSettlementMethod() != null ? req.getSettlementMethod() : SettlementMethod.FULL.name());
        master.setSpotRate(req.getSpotRate());
        master.setCustomerRate(customerRate);
        master.setCostRate(req.getCostRate());
        master.setBranchProfitPoint(req.getBranchProfitPoint());
        master.setSpecialTradeType(SpecialTradeType.NORMAL.name());
        master.setMakerId(currentUserId);
        master.setMakeTime(LocalDateTime.now());
        master.setVersion(Constants.DEFAULT_VERSION);
        master.setPurposeCode(req.getPurposeCode());
        master.setFxPurposeCode(req.getFxPurposeCode());
        fxTradeMasterMapper.insert(master);

        // 构建远期子表
        FxForwardTrade forwardTrade = new FxForwardTrade();
        forwardTrade.setTradeId(tradeId);
        forwardTrade.setMaturityDate(req.getMaturityDate());
        forwardTrade.setForwardRate(req.getCustomerRate());
        forwardTrade.setSettlementMethod(req.getSettlementMethod());
        forwardTrade.setCurrency1Account(req.getCurrency1Account());
        forwardTrade.setCurrency2Account(req.getCurrency2Account());
        forwardTrade.setMarginAccountId(req.getMarginAccountId());
        forwardTrade.setMarginAmount(marginAmount);
        forwardTrade.setAmount(req.getNotionalAmount());
        forwardTrade.setIsRolledOver(Constants.NO);
        forwardTrade.setEarlyDeliveryFlag(Constants.NO);
        forwardTrade.setEarlyDefaultFlag(Constants.NO);
        fxForwardTradeMapper.insert(forwardTrade);

        // 记录经办生命周期事件：null -> DRAFT
        tradeLifecycleService.recordEvent(tradeId, Constants.NODE_MAKE, currentUserId,
                null, TradeStatus.DRAFT.name(), req.getNotionalAmount(), customerRate,
                null, "远期交易经办录入");

        // 自动提交复核：DRAFT -> PENDING_CHECK
        submitToCheck(master, currentUserId, currentOrgCode);

        return tradeId;
    }

    /**
     * 创建掉期交易：生成主表与掉期子表（含近端/远端两腿），记录经办生命周期事件，
     * 随后自动提交复核（DRAFT->PENDING_CHECK）并创建复核任务
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createSwapTrade(SwapTradeEntryRequest req) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String currentOrgCode = SecurityUtils.getCurrentOrgCode();

        // 生成交易ID与业务编号
        String tradeId = generateTradeId(Constants.PREFIX_SWAP);
        String businessNo = generateBusinessNo(Constants.PREFIX_SWAP);

        // 查询客户名称
        String customerName = lookupCustomerName(req.getCustomerId());

        // 校验保证金账户余额并服务端重算保证金金额（以近端金额为交易金额 × 10%）
        BigDecimal marginAmount = validateAndCalcMargin(req.getMarginAccountId(), req.getNearLegAmount());

        // 构建交易主表，名义金额取近端金额
        FxTradeMaster master = new FxTradeMaster();
        master.setTradeId(tradeId);
        master.setBusinessNo(businessNo);
        master.setTradeType(TradeType.SWAP.name());
        master.setStatus(TradeStatus.DRAFT.name());
        master.setBranchCode(req.getBranchCode() != null ? req.getBranchCode() : currentOrgCode);
        master.setCustomerId(req.getCustomerId());
        master.setCustomerName(customerName);
        master.setBaseCurrency(req.getBaseCurrency());
        master.setQuoteCurrency(req.getQuoteCurrency());
        master.setCurrencyPair(req.getBaseCurrency() + "/" + req.getQuoteCurrency());
        master.setNotionalAmount(req.getNearLegAmount());
        master.setTradeDirection(req.getNearLegDirection());
        master.setValueDate(req.getNearLegValueDate());
        master.setTradeDate(req.getTradeDate());
        master.setMaturityDate(req.getFarLegValueDate());
        master.setCustomerRate(req.getCustomerRate());
        master.setCostRate(req.getCostRate());
        master.setSettlementMethod(SettlementMethod.FULL.name());
        master.setSpecialTradeType(SpecialTradeType.NORMAL.name());
        master.setMakerId(currentUserId);
        master.setMakeTime(LocalDateTime.now());
        master.setVersion(Constants.DEFAULT_VERSION);
        master.setPurposeCode(req.getPurposeCode());
        master.setFxPurposeCode(req.getFxPurposeCode());
        fxTradeMasterMapper.insert(master);

        // 构建掉期子表（近端、远端两腿）
        FxSwapTrade swapTrade = new FxSwapTrade();
        swapTrade.setTradeId(tradeId);
        swapTrade.setSwapType(req.getSwapType());
        swapTrade.setNearLegDirection(req.getNearLegDirection());
        swapTrade.setNearLegAmount(req.getNearLegAmount());
        swapTrade.setNearLegRate(req.getNearLegRate());
        swapTrade.setNearLegCostRate(req.getNearLegCostRate());
        swapTrade.setNearLegCustomerRate(req.getNearLegCustomerRate());
        swapTrade.setNearLegBranchProfitPoint(req.getNearLegBranchProfitPoint());
        swapTrade.setNearLegValueDate(req.getNearLegValueDate());
        swapTrade.setNearLegAccount(req.getNearLegAccount());
        swapTrade.setNearLegCurrency1Account(req.getNearLegCurrency1Account());
        swapTrade.setNearLegCurrency2Account(req.getNearLegCurrency2Account());
        swapTrade.setNearLegSettlementMethod(req.getNearLegSettlementMethod());
        swapTrade.setFarLegDirection(req.getFarLegDirection());
        swapTrade.setFarLegAmount(req.getFarLegAmount());
        swapTrade.setFarLegRate(req.getFarLegRate());
        swapTrade.setFarLegCostRate(req.getFarLegCostRate());
        swapTrade.setFarLegCustomerRate(req.getFarLegCustomerRate());
        swapTrade.setFarLegBranchProfitPoint(req.getFarLegBranchProfitPoint());
        swapTrade.setFarLegValueDate(req.getFarLegValueDate());
        swapTrade.setFarLegAccount(req.getFarLegAccount());
        swapTrade.setFarLegCurrency1Account(req.getFarLegCurrency1Account());
        swapTrade.setFarLegCurrency2Account(req.getFarLegCurrency2Account());
        swapTrade.setFarLegSettlementMethod(req.getFarLegSettlementMethod());
        swapTrade.setTerm(req.getTerm());
        swapTrade.setSwapPoint(req.getSwapPoint());
        swapTrade.setNearSpotRate(req.getNearSpotRate());
        swapTrade.setIsPureSwap(Constants.NO);
        swapTrade.setMarginAccountId(req.getMarginAccountId());
        swapTrade.setMarginAmount(marginAmount);
        fxSwapTradeMapper.insert(swapTrade);

        // 记录经办生命周期事件：null -> DRAFT
        tradeLifecycleService.recordEvent(tradeId, Constants.NODE_MAKE, currentUserId,
                null, TradeStatus.DRAFT.name(), req.getNearLegAmount(), req.getNearLegRate(),
                null, "掉期交易经办录入");

        // 自动提交复核：DRAFT -> PENDING_CHECK
        submitToCheck(master, currentUserId, currentOrgCode);

        return tradeId;
    }

    /**
     * 查询交易完整详情：主表 + 子表明细 + 生命周期事件 + 审批日志
     */
    @Override
    public TradeDetailVO getTradeDetail(String tradeId) {
        FxTradeMaster master = fxTradeMasterMapper.selectByTradeId(tradeId);
        if (master == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "交易不存在");
        }

        TradeDetailVO vo = new TradeDetailVO();
        vo.setMaster(master);
        vo.setMakerName(resolveUserName(master.getMakerId()));
        vo.setCheckerName(resolveUserName(master.getCheckerId()));

        // 根据交易类型查询对应子表明细
        String tradeType = master.getTradeType();
        if (TradeType.SPOT.name().equals(tradeType)) {
            vo.setSpotDetail(fxSpotTradeMapper.selectByTradeId(tradeId));
        } else if (TradeType.FORWARD.name().equals(tradeType)) {
            vo.setForwardDetail(fxForwardTradeMapper.selectByTradeId(tradeId));
        } else if (TradeType.SWAP.name().equals(tradeType)) {
            vo.setSwapDetail(fxSwapTradeMapper.selectByTradeId(tradeId));
        }

        vo.setLifecycleList(tradeLifecycleService.listByTradeId(tradeId));
        vo.setApprovalLogList(approvalLogService.listByTradeId(tradeId));
        return vo;
    }

    /**
     * 分页查询未到期交易：状态为生效且类型为远期/掉期
     * tab=swapNear 过滤起息日 > 今天（掉期近端未到期）
     * tab=swapFar 过滤到期日 > 今天（掉期远端未到期）
     */
    @Override
    public PageResponse<FxTradeMaster> listUnmatured(String businessNo, String tradeType, String currencyPair,
                                                     String branchCode, String customerId, String tab, int pageNum, int pageSize) {
        Map<String, Object> params = buildConditionParams(businessNo, tradeType, currencyPair, branchCode, customerId);
        params.put("statusList", List.of(TradeStatus.ACTIVE.name()));
        params.put("tradeTypeList", List.of(TradeType.FORWARD.name(), TradeType.SWAP.name()));
        // 根据tab添加日期过滤：swapNear=起息日>今天, swapFar=到期日>今天
        java.time.LocalDate today = java.time.LocalDate.now();
        if ("swapNear".equals(tab)) {
            params.put("valueDateAfter", today);
        } else if ("swapFar".equals(tab)) {
            params.put("maturityDateAfter", today);
        }
        return queryPage(params, pageNum, pageSize);
    }

    /**
     * 分页查询客户交易：支持业务编号、客户ID、交易类型、状态、特殊交易类型过滤
     */
    @Override
    public PageResponse<TradeResponse> listCustomerTrades(String businessNo, String customerId, String tradeType,
                                                          String status, String specialTradeType,
                                                          int pageNum, int pageSize) {
        Map<String, Object> params = new HashMap<>();
        params.put("businessNo", businessNo);
        params.put("customerId", customerId);
        params.put("tradeType", tradeType);
        params.put("status", status);
        params.put("specialTradeType", specialTradeType);
        params.put("offset", (pageNum - 1) * pageSize);
        params.put("pageSize", pageSize);

        long total = fxTradeMasterMapper.countByCondition(params);
        List<FxTradeMaster> list = fxTradeMasterMapper.selectByCondition(params);
        Map<Long, String> userNameMap = buildUserNameMap();
        List<TradeResponse> respList = new ArrayList<>();
        for (FxTradeMaster master : list) {
            TradeResponse resp = convertToResponse(master);
            resp.setMakerName(userNameMap.get(master.getMakerId()));
            resp.setCheckerName(userNameMap.get(master.getCheckerId()));
            respList.add(resp);
        }
        return new PageResponse<>(total, pageNum, pageSize, respList);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 生成交易ID：前缀 + yyyyMMddHHmmss + 3位随机数
     */
    private String generateTradeId(String prefix) {
        String timePart = LocalDateTime.now().format(TRADE_ID_FMT);
        int random = ThreadLocalRandom.current().nextInt(100, 1000);
        return prefix + timePart + random;
    }

    /**
     * 生成业务编号：前缀 + yyyyMMdd + 4位序列号（取当前时间戳后4位）
     */
    private String generateBusinessNo(String prefix) {
        String datePart = LocalDateTime.now().format(BIZ_NO_FMT);
        long seq = System.currentTimeMillis() % 10000;
        return prefix + datePart + String.format("%04d", seq);
    }

    /**
     * 根据客户ID查询客户名称，不存在则抛出业务异常
     */
    private String lookupCustomerName(String customerId) {
        FxCustomer customer = fxCustomerMapper.selectByCustomerId(customerId);
        if (customer == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "客户不存在：" + customerId);
        }
        return customer.getCustomerName();
    }

    /**
     * 自动提交复核：更新交易状态 DRAFT->PENDING_CHECK，记录提交生命周期事件，
     * 并创建复核任务指派给当前机构下的复核角色
     */
    private void submitToCheck(FxTradeMaster master, Long currentUserId, String currentOrgCode) {
        String tradeId = master.getTradeId();
        String beforeStatus = TradeStatus.DRAFT.name();
        String afterStatus = TradeStatus.PENDING_CHECK.name();

        // 更新主表状态
        master.setStatus(afterStatus);
        fxTradeMasterMapper.update(master);

        // 记录提交生命周期事件
        tradeLifecycleService.recordEvent(tradeId, "SUBMIT", currentUserId,
                beforeStatus, afterStatus, master.getNotionalAmount(), master.getCustomerRate(),
                null, "提交复核");

        // 查询机构层级
        Integer orgLevel = null;
        if (currentOrgCode != null) {
            FxOrg org = fxOrgMapper.selectByOrgCode(currentOrgCode);
            if (org != null) {
                orgLevel = org.getOrgLevel();
            }
        }

        // 创建复核任务，指派给当前机构的复核角色
        taskService.createTask(TaskType.CHECK.name(), tradeId, master.getBusinessNo(),
                master.getTradeType(), RoleCode.CHECKER.name(), currentOrgCode, orgLevel, null);
    }

    /**
     * 构造通用查询条件参数（不含分页与状态/类型列表）
     */
    private Map<String, Object> buildConditionParams(String businessNo, String tradeType, String currencyPair,
                                                     String branchCode, String customerId) {
        Map<String, Object> params = new HashMap<>();
        params.put("businessNo", businessNo);
        params.put("tradeType", tradeType);
        params.put("currencyPair", currencyPair);
        params.put("branchCode", branchCode);
        params.put("customerId", customerId);
        return params;
    }

    /**
     * 通用分页查询交易主表
     */
    private PageResponse<FxTradeMaster> queryPage(Map<String, Object> params, int pageNum, int pageSize) {
        params.put("offset", (pageNum - 1) * pageSize);
        params.put("pageSize", pageSize);
        long total = fxTradeMasterMapper.countByCondition(params);
        List<FxTradeMaster> list = fxTradeMasterMapper.selectByCondition(params);
        return new PageResponse<>(total, pageNum, pageSize, list);
    }

    /**
     * 将交易主表实体转换为交易响应 DTO
     */
    private TradeResponse convertToResponse(FxTradeMaster master) {
        TradeResponse resp = new TradeResponse();
        resp.setTradeId(master.getTradeId());
        resp.setBusinessNo(master.getBusinessNo());
        resp.setTradeType(master.getTradeType());
        resp.setStatus(master.getStatus());
        resp.setCustomerName(master.getCustomerName());
        resp.setCurrencyPair(master.getCurrencyPair());
        resp.setNotionalAmount(master.getNotionalAmount() != null ? master.getNotionalAmount().toPlainString() : null);
        resp.setTradeDirection(master.getTradeDirection());
        resp.setCustomerRate(master.getCustomerRate() != null ? master.getCustomerRate().toPlainString() : null);
        resp.setTradeDate(master.getTradeDate() != null ? master.getTradeDate().toString() : null);
        resp.setTradeTime(master.getMakeTime());
        resp.setMaturityDate(master.getMaturityDate() != null ? master.getMaturityDate().toString() : null);
        resp.setSettlementMethod(master.getSettlementMethod());
        resp.setSpecialTradeType(master.getSpecialTradeType());
        resp.setMakerId(master.getMakerId());
        resp.setCheckerId(master.getCheckerId());
        resp.setCreatedAt(master.getCreatedAt());
        return resp;
    }

    /**
     * 构建用户ID到姓名的映射（用于列表批量解析经办人/复核人姓名，避免 N+1 查询）
     */
    private Map<Long, String> buildUserNameMap() {
        Map<Long, String> map = new HashMap<>();
        for (FxUser user : fxUserMapper.selectAll()) {
            map.put(user.getUserId(), user.getRealName());
        }
        return map;
    }

    /**
     * 根据用户ID解析用户姓名（用于单条详情解析）
     */
    private String resolveUserName(Long userId) {
        if (userId == null) {
            return null;
        }
        FxUser user = fxUserMapper.selectByUserId(userId);
        return user != null ? user.getRealName() : null;
    }

    /**
     * 更新即期交易并重新提交复核
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String updateAndResubmitSpotTrade(String tradeId, SpotTradeEntryRequest req) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String currentOrgCode = SecurityUtils.getCurrentOrgCode();

        // 查询原交易
        FxTradeMaster master = getTradeOrThrow(tradeId);
        if (!TradeStatus.DRAFT.name().equals(master.getStatus())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "只有草稿状态的交易可以重新提交");
        }

        // 查询客户名称
        String customerName = lookupCustomerName(req.getCustomerId());

        // 计算对手金额 = 名义金额 × 客户汇率
        BigDecimal counterAmount = req.getNotionalAmount()
                .multiply(req.getCustomerRate())
                .setScale(2, RoundingMode.HALF_UP);

        // 校验保证金账户余额并服务端重算保证金金额（交易金额 × 10%）
        BigDecimal marginAmount = validateAndCalcMargin(req.getMarginAccountId(), req.getNotionalAmount());

        // 更新交易主表
        master.setBranchCode(req.getBranchCode() != null ? req.getBranchCode() : currentOrgCode);
        master.setCustomerId(req.getCustomerId());
        master.setCustomerName(customerName);
        master.setBaseCurrency(req.getBaseCurrency());
        master.setQuoteCurrency(req.getQuoteCurrency());
        master.setCurrencyPair(req.getBaseCurrency() + "/" + req.getQuoteCurrency());
        master.setNotionalAmount(req.getNotionalAmount());
        master.setCounterAmount(counterAmount);
        master.setTradeDirection(req.getTradeDirection());
        master.setValueDate(req.getValueDate());
        master.setTradeDate(req.getTradeDate());
        master.setMaturityDate(req.getTradeDate());
        master.setDeliveryType(req.getDeliveryType());
        master.setSpotRate(req.getSpotRate());
        master.setCustomerRate(req.getCustomerRate());
        master.setCostRate(req.getCostRate());
        master.setBranchProfitPoint(req.getBranchProfitPoint());
        master.setPurposeCode(req.getPurposeCode());
        master.setFxPurposeCode(req.getFxPurposeCode());
        master.setMakerId(currentUserId);
        master.setMakeTime(LocalDateTime.now());
        fxTradeMasterMapper.update(master);

        // 更新即期子表（先删除后插入）
        fxSpotTradeMapper.deleteByTradeId(tradeId);
        FxSpotTrade spotTrade = new FxSpotTrade();
        spotTrade.setTradeId(tradeId);
        spotTrade.setSettlementType(req.getDeliveryType());
        spotTrade.setSpotRate(req.getSpotRate());
        spotTrade.setCustomerRate(req.getCustomerRate());
        spotTrade.setCostRate(req.getCostRate());
        spotTrade.setCurrency1Account(req.getCurrency1Account());
        spotTrade.setCurrency2Account(req.getCurrency2Account());
        spotTrade.setMarginAccountId(req.getMarginAccountId());
        spotTrade.setMarginAmount(marginAmount);
        spotTrade.setAmount(req.getNotionalAmount());
        fxSpotTradeMapper.insert(spotTrade);

        // 记录经办生命周期事件
        tradeLifecycleService.recordEvent(tradeId, Constants.NODE_MAKE, currentUserId,
                TradeStatus.DRAFT.name(), TradeStatus.DRAFT.name(), req.getNotionalAmount(), req.getCustomerRate(),
                null, "即期交易重新编辑");

        // 重新提交复核
        submitToCheck(master, currentUserId, currentOrgCode);

        return tradeId;
    }

    /**
     * 更新远期交易并重新提交复核
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String updateAndResubmitForwardTrade(String tradeId, ForwardTradeEntryRequest req) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String currentOrgCode = SecurityUtils.getCurrentOrgCode();

        // 查询原交易
        FxTradeMaster master = getTradeOrThrow(tradeId);
        if (!TradeStatus.DRAFT.name().equals(master.getStatus())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "只有草稿状态的交易可以重新提交");
        }

        // 查询客户名称
        String customerName = lookupCustomerName(req.getCustomerId());

        // 计算对手金额 = 名义金额 × 客户汇率（远期交易以客户汇率作为实际成交汇率）
        BigDecimal customerRate = req.getCustomerRate();
        BigDecimal counterAmount = req.getNotionalAmount()
                .multiply(customerRate)
                .setScale(2, RoundingMode.HALF_UP);

        // 校验保证金账户余额并服务端重算保证金金额（交易金额 × 10%）
        BigDecimal marginAmount = validateAndCalcMargin(req.getMarginAccountId(), req.getNotionalAmount());

        // 更新交易主表
        master.setBranchCode(req.getBranchCode() != null ? req.getBranchCode() : currentOrgCode);
        master.setCustomerId(req.getCustomerId());
        master.setCustomerName(customerName);
        master.setBaseCurrency(req.getBaseCurrency());
        master.setQuoteCurrency(req.getQuoteCurrency());
        master.setCurrencyPair(req.getBaseCurrency() + "/" + req.getQuoteCurrency());
        master.setNotionalAmount(req.getNotionalAmount());
        master.setCounterAmount(counterAmount);
        master.setTradeDirection(req.getTradeDirection());
        // 远期交易起息日取交易日，到期日取 maturityDate
        master.setValueDate(req.getTradeDate());
        master.setTradeDate(req.getTradeDate());
        master.setMaturityDate(req.getMaturityDate());
        master.setDeliveryType(req.getDeliveryType());
        master.setSettlementMethod(req.getSettlementMethod() != null ? req.getSettlementMethod() : SettlementMethod.FULL.name());
        master.setSpotRate(req.getSpotRate());
        master.setCustomerRate(customerRate);
        master.setCostRate(req.getCostRate());
        master.setBranchProfitPoint(req.getBranchProfitPoint());
        master.setPurposeCode(req.getPurposeCode());
        master.setFxPurposeCode(req.getFxPurposeCode());
        master.setMakerId(currentUserId);
        master.setMakeTime(LocalDateTime.now());
        fxTradeMasterMapper.update(master);

        // 更新远期子表（先删除后插入）
        fxForwardTradeMapper.deleteByTradeId(tradeId);
        FxForwardTrade forwardTrade = new FxForwardTrade();
        forwardTrade.setTradeId(tradeId);
        forwardTrade.setMaturityDate(req.getMaturityDate());
        forwardTrade.setForwardRate(req.getCustomerRate());
        forwardTrade.setSettlementMethod(req.getSettlementMethod());
        forwardTrade.setCurrency1Account(req.getCurrency1Account());
        forwardTrade.setCurrency2Account(req.getCurrency2Account());
        forwardTrade.setMarginAccountId(req.getMarginAccountId());
        forwardTrade.setMarginAmount(marginAmount);
        forwardTrade.setAmount(req.getNotionalAmount());
        forwardTrade.setIsRolledOver(Constants.NO);
        forwardTrade.setEarlyDeliveryFlag(Constants.NO);
        forwardTrade.setEarlyDefaultFlag(Constants.NO);
        fxForwardTradeMapper.insert(forwardTrade);

        // 记录经办生命周期事件
        tradeLifecycleService.recordEvent(tradeId, Constants.NODE_MAKE, currentUserId,
                TradeStatus.DRAFT.name(), TradeStatus.DRAFT.name(), req.getNotionalAmount(), customerRate,
                null, "远期交易重新编辑");

        // 重新提交复核
        submitToCheck(master, currentUserId, currentOrgCode);

        return tradeId;
    }

    /**
     * 更新掉期交易并重新提交复核
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String updateAndResubmitSwapTrade(String tradeId, SwapTradeEntryRequest req) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String currentOrgCode = SecurityUtils.getCurrentOrgCode();

        // 查询原交易
        FxTradeMaster master = getTradeOrThrow(tradeId);
        if (!TradeStatus.DRAFT.name().equals(master.getStatus())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "只有草稿状态的交易可以重新提交");
        }

        // 查询客户名称
        String customerName = lookupCustomerName(req.getCustomerId());

        // 校验保证金账户余额并服务端重算保证金金额（以近端金额为交易金额 × 10%）
        BigDecimal marginAmount = validateAndCalcMargin(req.getMarginAccountId(), req.getNearLegAmount());

        // 更新交易主表，名义金额取近端金额
        master.setBranchCode(req.getBranchCode() != null ? req.getBranchCode() : currentOrgCode);
        master.setCustomerId(req.getCustomerId());
        master.setCustomerName(customerName);
        master.setBaseCurrency(req.getBaseCurrency());
        master.setQuoteCurrency(req.getQuoteCurrency());
        master.setCurrencyPair(req.getBaseCurrency() + "/" + req.getQuoteCurrency());
        master.setNotionalAmount(req.getNearLegAmount());
        master.setTradeDirection(req.getNearLegDirection());
        master.setValueDate(req.getNearLegValueDate());
        master.setTradeDate(req.getTradeDate());
        master.setMaturityDate(req.getFarLegValueDate());
        master.setCustomerRate(req.getCustomerRate());
        master.setCostRate(req.getCostRate());
        master.setSettlementMethod(SettlementMethod.FULL.name());
        master.setSpecialTradeType(SpecialTradeType.NORMAL.name());
        master.setPurposeCode(req.getPurposeCode());
        master.setFxPurposeCode(req.getFxPurposeCode());
        master.setMakerId(currentUserId);
        master.setMakeTime(LocalDateTime.now());
        fxTradeMasterMapper.update(master);

        // 更新掉期子表（先删除后插入）
        fxSwapTradeMapper.deleteByTradeId(tradeId);
        FxSwapTrade swapTrade = new FxSwapTrade();
        swapTrade.setTradeId(tradeId);
        swapTrade.setSwapType(req.getSwapType());
        swapTrade.setNearLegDirection(req.getNearLegDirection());
        swapTrade.setNearLegAmount(req.getNearLegAmount());
        swapTrade.setNearLegRate(req.getNearLegRate());
        swapTrade.setNearLegCostRate(req.getNearLegCostRate());
        swapTrade.setNearLegCustomerRate(req.getNearLegCustomerRate());
        swapTrade.setNearLegBranchProfitPoint(req.getNearLegBranchProfitPoint());
        swapTrade.setNearLegValueDate(req.getNearLegValueDate());
        swapTrade.setNearLegAccount(req.getNearLegAccount());
        swapTrade.setNearLegCurrency1Account(req.getNearLegCurrency1Account());
        swapTrade.setNearLegCurrency2Account(req.getNearLegCurrency2Account());
        swapTrade.setNearLegSettlementMethod(req.getNearLegSettlementMethod());
        swapTrade.setFarLegDirection(req.getFarLegDirection());
        swapTrade.setFarLegAmount(req.getFarLegAmount());
        swapTrade.setFarLegRate(req.getFarLegRate());
        swapTrade.setFarLegCostRate(req.getFarLegCostRate());
        swapTrade.setFarLegCustomerRate(req.getFarLegCustomerRate());
        swapTrade.setFarLegBranchProfitPoint(req.getFarLegBranchProfitPoint());
        swapTrade.setFarLegValueDate(req.getFarLegValueDate());
        swapTrade.setFarLegAccount(req.getFarLegAccount());
        swapTrade.setFarLegCurrency1Account(req.getFarLegCurrency1Account());
        swapTrade.setFarLegCurrency2Account(req.getFarLegCurrency2Account());
        swapTrade.setFarLegSettlementMethod(req.getFarLegSettlementMethod());
        swapTrade.setTerm(req.getTerm());
        swapTrade.setSwapPoint(req.getSwapPoint());
        swapTrade.setNearSpotRate(req.getNearSpotRate());
        swapTrade.setIsPureSwap(Constants.NO);
        swapTrade.setMarginAccountId(req.getMarginAccountId());
        swapTrade.setMarginAmount(marginAmount);
        fxSwapTradeMapper.insert(swapTrade);

        // 记录经办生命周期事件
        tradeLifecycleService.recordEvent(tradeId, Constants.NODE_MAKE, currentUserId,
                TradeStatus.DRAFT.name(), TradeStatus.DRAFT.name(), req.getNearLegAmount(), req.getCustomerRate(),
                null, "掉期交易重新编辑");

        // 重新提交复核
        submitToCheck(master, currentUserId, currentOrgCode);

        return tradeId;
    }

    /**
     * 根据交易ID查询交易，不存在则抛出业务异常
     */
    private FxTradeMaster getTradeOrThrow(String tradeId) {
        FxTradeMaster master = fxTradeMasterMapper.selectByTradeId(tradeId);
        if (master == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "交易不存在：" + tradeId);
        }
        return master;
    }
}
