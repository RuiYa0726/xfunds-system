package com.xfunds.service.impl;

import com.xfunds.common.BusinessException;
import com.xfunds.common.Constants;
import com.xfunds.common.ResultCode;
import com.xfunds.common.SecurityUtils;
import com.xfunds.dto.OptionAbandonRequest;
import com.xfunds.dto.OptionCloseRequest;
import com.xfunds.dto.OptionExerciseRequest;
import com.xfunds.dto.OptionPremiumSettleRequest;
import com.xfunds.dto.OptionReminderVO;
import com.xfunds.dto.OptionTradeDetailVO;
import com.xfunds.dto.OptionTradeEntryRequest;
import com.xfunds.dto.PageResponse;
import com.xfunds.entity.FxCustomer;
import com.xfunds.entity.FxOptionTrade;
import com.xfunds.entity.FxOrg;
import com.xfunds.entity.FxTradeMaster;
import com.xfunds.enums.OptionStyle;
import com.xfunds.enums.RoleCode;
import com.xfunds.enums.SpecialTradeType;
import com.xfunds.enums.TaskType;
import com.xfunds.enums.TradeStatus;
import com.xfunds.enums.TradeType;
import com.xfunds.mapper.FxCustomerMapper;
import com.xfunds.mapper.FxOptionTradeMapper;
import com.xfunds.mapper.FxOrgMapper;
import com.xfunds.mapper.FxTradeMasterMapper;
import com.xfunds.service.ApprovalLogService;
import com.xfunds.service.MarginService;
import com.xfunds.service.OptionTradeService;
import com.xfunds.service.TaskService;
import com.xfunds.service.TradeLifecycleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 期权交易服务实现类
 * 处理期权交易录入、工作台提醒、行权、平仓、放弃、期权费交割等全生命周期操作
 */
@Service
public class OptionTradeServiceImpl implements OptionTradeService {

    /** 交易ID时间格式：yyyyMMddHHmmss */
    private static final DateTimeFormatter TRADE_ID_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 业务编号日期格式：yyyyMMdd */
    private static final DateTimeFormatter BIZ_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired
    private FxTradeMasterMapper fxTradeMasterMapper;

    @Autowired
    private FxOptionTradeMapper fxOptionTradeMapper;

    @Autowired
    private FxCustomerMapper fxCustomerMapper;

    @Autowired
    private FxOrgMapper fxOrgMapper;

    @Autowired
    private TradeLifecycleService tradeLifecycleService;

    @Autowired
    private ApprovalLogService approvalLogService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private MarginService marginService;

    // ==================== 期权交易录入 ====================

    /**
     * 创建期权交易：生成主表与期权子表，记录经办生命周期事件，
     * 随后自动提交复核（DRAFT->PENDING_CHECK）并创建复核任务
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createOption(OptionTradeEntryRequest req) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String currentOrgCode = SecurityUtils.getCurrentOrgCode();

        // 生成交易ID与业务编号
        String tradeId = generateTradeId(Constants.PREFIX_OPTION);
        String businessNo = generateBusinessNo(Constants.PREFIX_OPTION);

        // 查询客户名称
        String customerName = lookupCustomerName(req.getCustomerId());

        // 构建交易主表
        FxTradeMaster master = new FxTradeMaster();
        master.setTradeId(tradeId);
        master.setBusinessNo(businessNo);
        master.setTradeType(TradeType.OPTION.name());
        master.setStatus(TradeStatus.DRAFT.name());
        master.setBranchCode(req.getBranchCode() != null ? req.getBranchCode() : currentOrgCode);
        master.setCustomerId(req.getCustomerId());
        master.setCustomerName(customerName);
        master.setBaseCurrency(req.getBaseCurrency());
        master.setQuoteCurrency(req.getQuoteCurrency());
        master.setCurrencyPair(req.getCurrencyPair() != null ? req.getCurrencyPair()
                : req.getBaseCurrency() + "/" + req.getQuoteCurrency());
        master.setNotionalAmount(req.getNotionalAmount());
        master.setTradeDirection(req.getBuyerSeller());
        master.setValueDate(req.getTradeDate());
        master.setTradeDate(req.getTradeDate());
        master.setMaturityDate(req.getMaturityDate());
        master.setDeliveryType(req.getDeliveryType());
        master.setSettlementMethod(req.getSettlementMethod());
        master.setSpotRate(req.getSpotRate());
        master.setCustomerRate(req.getStrikePrice());
        master.setSpecialTradeType(SpecialTradeType.NORMAL.name());
        master.setMakerId(currentUserId);
        master.setMakeTime(LocalDateTime.now());
        master.setVersion(Constants.DEFAULT_VERSION);
        master.setPurposeCode(req.getPurposeCode());
        master.setFxPurposeCode(req.getFxPurposeCode());
        fxTradeMasterMapper.insert(master);

        // 构建期权子表
        FxOptionTrade optionTrade = new FxOptionTrade();
        optionTrade.setTradeId(tradeId);
        optionTrade.setOptionType(req.getOptionType());
        optionTrade.setOptionStyle(req.getOptionStyle());
        optionTrade.setStrikePrice(req.getStrikePrice());
        optionTrade.setPremiumAmount(req.getPremiumAmount());
        optionTrade.setPremiumCurrency(req.getPremiumCurrency());
        optionTrade.setPremiumValueDate(req.getPremiumValueDate());
        optionTrade.setPremiumPaidFlag("0");
        optionTrade.setPremiumAccountId(req.getPremiumAccountId());
        optionTrade.setMaturityDate(req.getMaturityDate());
        optionTrade.setExerciseFlag("0");
        optionTrade.setAbandonFlag("0");
        optionTrade.setSettlementMethod(req.getSettlementMethod());
        optionTrade.setBuyerSeller(req.getBuyerSeller());
        optionTrade.setCurrency1Account(req.getCurrency1Account());
        optionTrade.setCurrency2Account(req.getCurrency2Account());
        optionTrade.setNotionalAmount(req.getNotionalAmount());
        optionTrade.setCurrency1Amount(req.getCurrency1Amount());
        optionTrade.setCurrency2Amount(req.getCurrency2Amount());
        optionTrade.setObservationStartDate(req.getObservationStartDate());
        optionTrade.setObservationEndDate(req.getObservationEndDate());
        optionTrade.setExerciseTimePoint(req.getExerciseTimePoint());
        optionTrade.setDays(req.getDays());
        optionTrade.setClosedAmount(BigDecimal.ZERO);
        optionTrade.setRemainingAmount(req.getNotionalAmount());
        // 录入时以即期汇率作为参考汇率，用于工作台实值判断
        optionTrade.setReferenceRate(req.getSpotRate());
        fxOptionTradeMapper.insert(optionTrade);

        // 记录经办生命周期事件：null -> DRAFT
        tradeLifecycleService.recordEvent(tradeId, Constants.NODE_MAKE, currentUserId,
                null, TradeStatus.DRAFT.name(), req.getNotionalAmount(), req.getStrikePrice(),
                null, "期权交易经办录入");

        // 自动提交复核：DRAFT -> PENDING_CHECK
        submitToCheck(master, currentUserId, currentOrgCode);

        return tradeId;
    }

    /**
     * 查询期权交易完整详情：主表 + 期权子表明细 + 生命周期事件 + 审批日志
     */
    @Override
    public OptionTradeDetailVO getOptionDetail(String tradeId) {
        FxTradeMaster master = fxTradeMasterMapper.selectByTradeId(tradeId);
        if (master == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "期权交易不存在：" + tradeId);
        }

        OptionTradeDetailVO vo = new OptionTradeDetailVO();
        vo.setMaster(master);
        vo.setOptionDetail(fxOptionTradeMapper.selectByTradeId(tradeId));
        vo.setLifecycleList(tradeLifecycleService.listByTradeId(tradeId));
        vo.setApprovalLogList(approvalLogService.listByTradeId(tradeId));
        return vo;
    }

    /**
     * 分页查询期权交易列表：先查期权子表获取交易ID列表，再查主表分页
     */
    @Override
    public PageResponse<FxTradeMaster> listOptions(String businessNo, String customerId, String optionStyle,
                                                   String optionType, String status, int pageNum, int pageSize) {
        // 构建主表查询条件
        Map<String, Object> params = new HashMap<>();
        params.put("businessNo", businessNo);
        params.put("customerId", customerId);
        params.put("tradeType", TradeType.OPTION.name());
        params.put("status", status);
        params.put("offset", (pageNum - 1) * pageSize);
        params.put("pageSize", pageSize);

        // 若指定了期权属性过滤，则先查期权子表获取交易ID列表
        if ((optionStyle != null && !optionStyle.isEmpty()) || (optionType != null && !optionType.isEmpty())) {
            Map<String, Object> optionParams = new HashMap<>();
            optionParams.put("optionStyle", optionStyle);
            optionParams.put("optionType", optionType);
            optionParams.put("offset", 0);
            optionParams.put("pageSize", Integer.MAX_VALUE);
            List<FxOptionTrade> optionList = fxOptionTradeMapper.selectByCondition(optionParams);
            List<String> tradeIdList = optionList.stream()
                    .map(FxOptionTrade::getTradeId)
                    .collect(Collectors.toList());
            if (tradeIdList.isEmpty()) {
                return new PageResponse<>(0L, pageNum, pageSize, new ArrayList<>());
            }
            params.put("tradeIdList", tradeIdList);
        }

        long total = fxTradeMasterMapper.countByCondition(params);
        List<FxTradeMaster> list = fxTradeMasterMapper.selectByCondition(params);
        return new PageResponse<>(total, pageNum, pageSize, list);
    }

    /**
     * 查询已平仓的期权交易列表（close_date 不为空）
     */
    @Override
    public PageResponse<FxTradeMaster> listCloseTrades(String businessNo, String customerId,
                                                       int pageNum, int pageSize) {
        return listOptionsByOptionFlag(businessNo, customerId, pageNum, pageSize,
                Map.of("closeDateNotNull", true));
    }

    /**
     * 查询期权费已交割的期权交易列表（premium_paid_flag = 1）
     */
    @Override
    public PageResponse<FxTradeMaster> listPremiumTrades(String businessNo, String customerId,
                                                         int pageNum, int pageSize) {
        return listOptionsByOptionFlag(businessNo, customerId, pageNum, pageSize,
                Map.of("premiumPaidFlag", "1"));
    }

    /**
     * 查询已行权的期权交易列表（exercise_flag = 1）
     */
    @Override
    public PageResponse<FxTradeMaster> listExerciseTrades(String businessNo, String customerId,
                                                          int pageNum, int pageSize) {
        return listOptionsByOptionFlag(businessNo, customerId, pageNum, pageSize,
                Map.of("exerciseFlag", "1"));
    }

    /**
     * 查询已放弃的期权交易列表（abandon_flag = 1）
     */
    @Override
    public PageResponse<FxTradeMaster> listAbandonTrades(String businessNo, String customerId,
                                                         int pageNum, int pageSize) {
        return listOptionsByOptionFlag(businessNo, customerId, pageNum, pageSize,
                Map.of("abandonFlag", "1"));
    }

    // ==================== 期权工作台 ====================

    /**
     * 查询美式期权实值提醒列表：
     * 1. 查询美式期权中处于实值状态（CALL: 参考汇率 > 执行价；PUT: 参考汇率 < 执行价）的交易
     * 2. 关联主表过滤状态为生效或期权费已结清
     * 3. 组装提醒视图返回
     */
    @Override
    public List<OptionReminderVO> getAmericanInMoneyReminders() {
        // 查询美式期权实值交易
        List<FxOptionTrade> inMoneyOptions = fxOptionTradeMapper.selectAmericanInMoney();
        if (inMoneyOptions.isEmpty()) {
            return new ArrayList<>();
        }

        List<OptionReminderVO> voList = new ArrayList<>();
        for (FxOptionTrade option : inMoneyOptions) {
            FxTradeMaster master = fxTradeMasterMapper.selectByTradeId(option.getTradeId());
            if (master == null) {
                continue;
            }
            // 仅返回生效或期权费已结清状态的交易
            String status = master.getStatus();
            if (!TradeStatus.ACTIVE.name().equals(status)
                    && !TradeStatus.PREMIUM_SETTLED.name().equals(status)) {
                continue;
            }
            voList.add(convertToReminderVO(master, option));
        }
        return voList;
    }

    /**
     * 查看原始期权交易详情（与 getOptionDetail 相同）
     */
    @Override
    public OptionTradeDetailVO viewOriginalTrade(String tradeId) {
        return getOptionDetail(tradeId);
    }

    /**
     * 行权：
     * 1. 校验交易为期权类型且状态为生效或期权费已结清
     * 2. 美式期权到期前可随时行权，欧式期权需到期日及以后行权
     * 3. 更新期权子表行权标志、行权日、参考汇率
     * 4. 主表状态变更为已行权，记录生命周期事件
     * 5. 若交割方式为净额交割，计算净额交割金额
     * 6. 创建生命周期复核任务
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void executeOption(String tradeId, OptionExerciseRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        FxTradeMaster master = getTradeOrThrow(tradeId);
        validateOptionType(master);
        validateActiveOrPremiumSettled(master);

        FxOptionTrade option = fxOptionTradeMapper.selectByTradeId(tradeId);
        if (option == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "期权交易明细不存在：" + tradeId);
        }

        // 行权时机校验：欧式期权需到期日及以后方可行权
        validateExerciseTiming(option, request.getExerciseDate());

        String beforeStatus = master.getStatus();
        String afterStatus = TradeStatus.EXERCISED.name();

        // 更新期权子表：行权标志、行权日、参考汇率
        option.setExerciseFlag("1");
        option.setExerciseDate(request.getExerciseDate());
        option.setReferenceRate(request.getReferenceRate());
        if (request.getSettlementAccount() != null) {
            option.setPremiumAccountId(request.getSettlementAccount());
        }
        fxOptionTradeMapper.update(option);

        // 更新主表状态
        master.setStatus(afterStatus);
        fxTradeMasterMapper.update(master);

        // 记录生命周期事件
        tradeLifecycleService.recordEvent(tradeId, "EXERCISE", currentUserId,
                beforeStatus, afterStatus, option.getNotionalAmount(), request.getReferenceRate(),
                null, "期权行权：" + request.getRemark());

        // 净额交割时计算净额交割金额：CALL 时 (参考汇率 - 执行价) * 名义金额
        if ("NET".equals(option.getSettlementMethod())) {
            BigDecimal netSettlement = calculateNetSettlement(option);
            tradeLifecycleService.recordEvent(tradeId, "EXERCISE_NET", currentUserId,
                    afterStatus, afterStatus, netSettlement, request.getReferenceRate(),
                    null, "期权行权净额交割计算");
        }

        // 创建生命周期复核任务
        createLifecycleCheckTask(master, currentUserId);
    }

    /**
     * 暂不处理：仅记录生命周期事件，不改变交易状态
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void postponeReminder(String tradeId, String remark) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        FxTradeMaster master = getTradeOrThrow(tradeId);
        validateOptionType(master);

        String status = master.getStatus();
        tradeLifecycleService.recordEvent(tradeId, "POSTPONE", currentUserId,
                status, status, null, null, null, "暂不处理：" + remark);
    }

    // ==================== 期权存续期管理 ====================

    /**
     * 分页查询未到期期权交易（状态为生效或期权费已结清且到期日大于今天）
     */
    @Override
    public PageResponse<FxTradeMaster> listUnmaturedOptions(String businessNo, String customerId,
                                                            String optionStyle, int pageNum, int pageSize) {
        Map<String, Object> params = new HashMap<>();
        params.put("businessNo", businessNo);
        params.put("customerId", customerId);
        params.put("tradeType", TradeType.OPTION.name());
        params.put("statusList", List.of(TradeStatus.ACTIVE.name(), TradeStatus.PREMIUM_SETTLED.name()));
        params.put("maturityDateAfter", LocalDate.now());
        params.put("offset", (pageNum - 1) * pageSize);
        params.put("pageSize", pageSize);

        // 若指定了行权方式，先查期权子表过滤交易ID
        if (optionStyle != null && !optionStyle.isEmpty()) {
            Map<String, Object> optionParams = new HashMap<>();
            optionParams.put("optionStyle", optionStyle);
            optionParams.put("offset", 0);
            optionParams.put("pageSize", Integer.MAX_VALUE);
            List<FxOptionTrade> optionList = fxOptionTradeMapper.selectByCondition(optionParams);
            List<String> tradeIdList = optionList.stream()
                    .map(FxOptionTrade::getTradeId)
                    .collect(Collectors.toList());
            if (tradeIdList.isEmpty()) {
                return new PageResponse<>(0L, pageNum, pageSize, new ArrayList<>());
            }
            params.put("tradeIdList", tradeIdList);
        }

        long total = fxTradeMasterMapper.countByCondition(params);
        List<FxTradeMaster> list = fxTradeMasterMapper.selectByCondition(params);
        return new PageResponse<>(total, pageNum, pageSize, list);
    }

    /**
     * 平仓：
     * 1. 校验交易为期权类型且状态为生效或期权费已结清
     * 2. 更新期权子表平仓信息：平仓日、平仓期权费、平仓盈亏、累计已平仓金额、剩余金额
     * 3. 若剩余金额为0，主表状态置为已平仓
     * 4. 记录生命周期事件，创建生命周期复核任务
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closeOption(String tradeId, OptionCloseRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        FxTradeMaster master = getTradeOrThrow(tradeId);
        validateOptionType(master);
        validateActiveOrPremiumSettled(master);

        FxOptionTrade option = fxOptionTradeMapper.selectByTradeId(tradeId);
        if (option == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "期权交易明细不存在：" + tradeId);
        }

        // 计算累计已平仓金额与剩余金额
        BigDecimal closedAmount = option.getClosedAmount() == null ? BigDecimal.ZERO : option.getClosedAmount();
        BigDecimal newClosedAmount = closedAmount.add(request.getCloseAmount());
        BigDecimal remainingAmount = option.getRemainingAmount() == null ? option.getNotionalAmount()
                : option.getRemainingAmount();
        BigDecimal newRemainingAmount = remainingAmount.subtract(request.getCloseAmount());

        // 更新期权子表平仓信息
        option.setCloseDate(request.getCloseDate());
        option.setClosePremium(request.getClosePremium());
        option.setClosePnl(request.getClosePnl());
        option.setClosedAmount(newClosedAmount);
        option.setRemainingAmount(newRemainingAmount);
        if (request.getSettlementAccount() != null) {
            option.setPremiumAccountId(request.getSettlementAccount());
        }
        fxOptionTradeMapper.update(option);

        // 若剩余金额为0，主表状态置为已平仓
        String beforeStatus = master.getStatus();
        String afterStatus;
        if (newRemainingAmount.compareTo(BigDecimal.ZERO) <= 0) {
            afterStatus = TradeStatus.CLOSED.name();
            master.setStatus(afterStatus);
            fxTradeMasterMapper.update(master);
        } else {
            afterStatus = beforeStatus;
        }

        // 记录生命周期事件
        tradeLifecycleService.recordEvent(tradeId, "CLOSE", currentUserId,
                beforeStatus, afterStatus, request.getCloseAmount(), request.getClosePremium(),
                null, "期权平仓：" + request.getRemark());

        // 创建生命周期复核任务
        createLifecycleCheckTask(master, currentUserId);
    }

    /**
     * 分页查询已到期的欧式期权交易（状态为期权费已结清且到期日小于等于今天）
     */
    @Override
    public PageResponse<FxTradeMaster> listEuropeanMaturedOptions(String businessNo, String customerId,
                                                                  int pageNum, int pageSize) {
        // 先查欧式期权的交易ID列表
        Map<String, Object> optionParams = new HashMap<>();
        optionParams.put("optionStyle", OptionStyle.EUROPEAN.name());
        optionParams.put("offset", 0);
        optionParams.put("pageSize", Integer.MAX_VALUE);
        List<FxOptionTrade> optionList = fxOptionTradeMapper.selectByCondition(optionParams);
        List<String> tradeIdList = optionList.stream()
                .map(FxOptionTrade::getTradeId)
                .collect(Collectors.toList());
        if (tradeIdList.isEmpty()) {
            return new PageResponse<>(0L, pageNum, pageSize, new ArrayList<>());
        }

        Map<String, Object> params = new HashMap<>();
        params.put("businessNo", businessNo);
        params.put("customerId", customerId);
        params.put("tradeType", TradeType.OPTION.name());
        params.put("status", TradeStatus.PREMIUM_SETTLED.name());
        params.put("tradeIdList", tradeIdList);
        params.put("maturityDateBeforeOrEqual", LocalDate.now());
        params.put("offset", (pageNum - 1) * pageSize);
        params.put("pageSize", pageSize);

        long total = fxTradeMasterMapper.countByCondition(params);
        List<FxTradeMaster> list = fxTradeMasterMapper.selectByCondition(params);
        return new PageResponse<>(total, pageNum, pageSize, list);
    }

    /**
     * 放弃：
     * 1. 校验交易为期权类型且状态为生效或期权费已结清
     * 2. 更新期权子表放弃标志
     * 3. 主表状态置为已放弃
     * 4. 记录生命周期事件，创建生命周期复核任务
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void abandonOption(String tradeId, OptionAbandonRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        FxTradeMaster master = getTradeOrThrow(tradeId);
        validateOptionType(master);
        validateActiveOrPremiumSettled(master);

        FxOptionTrade option = fxOptionTradeMapper.selectByTradeId(tradeId);
        if (option == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "期权交易明细不存在：" + tradeId);
        }

        // 更新期权子表放弃标志
        option.setAbandonFlag("1");
        fxOptionTradeMapper.update(option);

        // 主表状态置为已放弃
        String beforeStatus = master.getStatus();
        String afterStatus = TradeStatus.ABANDONED.name();
        master.setStatus(afterStatus);
        fxTradeMasterMapper.update(master);

        // 记录生命周期事件
        tradeLifecycleService.recordEvent(tradeId, "ABANDON", currentUserId,
                beforeStatus, afterStatus, option.getNotionalAmount(), option.getStrikePrice(),
                null, "期权放弃：" + request.getRemark());

        // 创建生命周期复核任务
        createLifecycleCheckTask(master, currentUserId);
    }

    /**
     * 期权费交割：
     * 1. 校验交易为期权类型且状态为生效
     * 2. 更新期权子表期权费支付标志与账户
     * 3. 主表状态从生效转为期权费已结清
     * 4. 记录生命周期事件，创建生命周期复核任务
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void premiumSettle(String tradeId, OptionPremiumSettleRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        FxTradeMaster master = getTradeOrThrow(tradeId);
        validateOptionType(master);

        // 期权费交割要求状态为生效
        if (!TradeStatus.ACTIVE.name().equals(master.getStatus())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "交易当前状态不允许期权费交割，需为生效状态");
        }

        FxOptionTrade option = fxOptionTradeMapper.selectByTradeId(tradeId);
        if (option == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "期权交易明细不存在：" + tradeId);
        }

        // 更新期权子表期权费支付标志与账户
        option.setPremiumPaidFlag("1");
        if (request.getSettlementAccount() != null) {
            option.setPremiumAccountId(request.getSettlementAccount());
        }
        fxOptionTradeMapper.update(option);

        // 主表状态从生效转为期权费已结清
        String beforeStatus = master.getStatus();
        String afterStatus = TradeStatus.PREMIUM_SETTLED.name();
        master.setStatus(afterStatus);
        fxTradeMasterMapper.update(master);

        // 记录生命周期事件
        tradeLifecycleService.recordEvent(tradeId, "PREMIUM_SETTLE", currentUserId,
                beforeStatus, afterStatus, option.getPremiumAmount(), null,
                null, "期权费交割：" + request.getRemark());

        // 创建生命周期复核任务
        createLifecycleCheckTask(master, currentUserId);
    }

    /**
     * 分页查询美式期权监控期交易（状态为期权费已结清且到期日大于今天）
     */
    @Override
    public PageResponse<FxTradeMaster> listAmericanMonitoring(String businessNo, String customerId,
                                                              int pageNum, int pageSize) {
        // 先查美式期权的交易ID列表
        Map<String, Object> optionParams = new HashMap<>();
        optionParams.put("optionStyle", OptionStyle.AMERICAN.name());
        optionParams.put("offset", 0);
        optionParams.put("pageSize", Integer.MAX_VALUE);
        List<FxOptionTrade> optionList = fxOptionTradeMapper.selectByCondition(optionParams);
        List<String> tradeIdList = optionList.stream()
                .map(FxOptionTrade::getTradeId)
                .collect(Collectors.toList());
        if (tradeIdList.isEmpty()) {
            return new PageResponse<>(0L, pageNum, pageSize, new ArrayList<>());
        }

        Map<String, Object> params = new HashMap<>();
        params.put("businessNo", businessNo);
        params.put("customerId", customerId);
        params.put("tradeType", TradeType.OPTION.name());
        params.put("status", TradeStatus.PREMIUM_SETTLED.name());
        params.put("tradeIdList", tradeIdList);
        params.put("maturityDateAfter", LocalDate.now());
        params.put("offset", (pageNum - 1) * pageSize);
        params.put("pageSize", pageSize);

        long total = fxTradeMasterMapper.countByCondition(params);
        List<FxTradeMaster> list = fxTradeMasterMapper.selectByCondition(params);
        return new PageResponse<>(total, pageNum, pageSize, list);
    }

    /**
     * 分页查询已到期的美式期权交易（状态为期权费已结清且到期日小于等于今天）
     */
    @Override
    public PageResponse<FxTradeMaster> listAmericanMaturedOptions(String businessNo, String customerId,
                                                                  int pageNum, int pageSize) {
        // 先查美式期权的交易ID列表
        Map<String, Object> optionParams = new HashMap<>();
        optionParams.put("optionStyle", OptionStyle.AMERICAN.name());
        optionParams.put("offset", 0);
        optionParams.put("pageSize", Integer.MAX_VALUE);
        List<FxOptionTrade> optionList = fxOptionTradeMapper.selectByCondition(optionParams);
        List<String> tradeIdList = optionList.stream()
                .map(FxOptionTrade::getTradeId)
                .collect(Collectors.toList());
        if (tradeIdList.isEmpty()) {
            return new PageResponse<>(0L, pageNum, pageSize, new ArrayList<>());
        }

        Map<String, Object> params = new HashMap<>();
        params.put("businessNo", businessNo);
        params.put("customerId", customerId);
        params.put("tradeType", TradeType.OPTION.name());
        params.put("status", TradeStatus.PREMIUM_SETTLED.name());
        params.put("tradeIdList", tradeIdList);
        params.put("maturityDateBeforeOrEqual", LocalDate.now());
        params.put("offset", (pageNum - 1) * pageSize);
        params.put("pageSize", pageSize);

        long total = fxTradeMasterMapper.countByCondition(params);
        List<FxTradeMaster> list = fxTradeMasterMapper.selectByCondition(params);
        return new PageResponse<>(total, pageNum, pageSize, list);
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
     * 根据期权子表标志条件分页查询期权交易主表
     *
     * @param businessNo   业务编号
     * @param customerId   客户ID
     * @param pageNum      页码
     * @param pageSize     每页大小
     * @param optionFlags  期权子表过滤条件（如 exerciseFlag、abandonFlag 等）
     * @return 分页结果
     */
    private PageResponse<FxTradeMaster> listOptionsByOptionFlag(String businessNo, String customerId,
                                                                int pageNum, int pageSize,
                                                                Map<String, Object> optionFlags) {
        // 先查期权子表获取符合条件的交易ID列表
        Map<String, Object> optionParams = new HashMap<>(optionFlags);
        optionParams.put("offset", 0);
        optionParams.put("pageSize", Integer.MAX_VALUE);
        List<FxOptionTrade> optionList = fxOptionTradeMapper.selectByCondition(optionParams);
        List<String> tradeIdList = optionList.stream()
                .map(FxOptionTrade::getTradeId)
                .collect(Collectors.toList());
        if (tradeIdList.isEmpty()) {
            return new PageResponse<>(0L, pageNum, pageSize, new ArrayList<>());
        }

        // 再查主表分页
        Map<String, Object> params = new HashMap<>();
        params.put("businessNo", businessNo);
        params.put("customerId", customerId);
        params.put("tradeType", TradeType.OPTION.name());
        params.put("tradeIdList", tradeIdList);
        params.put("offset", (pageNum - 1) * pageSize);
        params.put("pageSize", pageSize);

        long total = fxTradeMasterMapper.countByCondition(params);
        List<FxTradeMaster> list = fxTradeMasterMapper.selectByCondition(params);
        return new PageResponse<>(total, pageNum, pageSize, list);
    }

    /**
     * 将交易主表与期权子表组装为提醒视图对象
     */
    private OptionReminderVO convertToReminderVO(FxTradeMaster master, FxOptionTrade option) {
        OptionReminderVO vo = new OptionReminderVO();
        vo.setTradeId(master.getTradeId());
        vo.setBusinessNo(master.getBusinessNo());
        vo.setBuyerSeller(option.getBuyerSeller());
        // 价格方向根据期权类型推断：CALL 为涨，PUT 为跌
        vo.setPriceDirection("CALL".equals(option.getOptionType()) ? "UP" : "DOWN");
        vo.setCurrencyPair(master.getCurrencyPair());
        vo.setReferenceRate(option.getReferenceRate());
        vo.setStrikePrice(option.getStrikePrice());
        vo.setOptionStatus(master.getStatus());
        vo.setOriginalAmount(option.getNotionalAmount());
        vo.setClosedAmount(option.getClosedAmount());
        vo.setRemainingAmount(option.getRemainingAmount());
        vo.setObservationStartDate(option.getObservationStartDate());
        vo.setObservationEndDate(option.getObservationEndDate());
        vo.setTradeDate(master.getTradeDate());
        vo.setCurrency1Amount(option.getCurrency1Amount());
        vo.setCurrency2Amount(option.getCurrency2Amount());
        vo.setCustomerId(master.getCustomerId());
        vo.setCustomerName(master.getCustomerName());
        vo.setDeliveryType(master.getDeliveryType());
        vo.setOptionType(option.getOptionType());
        vo.setOptionStyle(option.getOptionStyle());
        return vo;
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

    /**
     * 校验交易类型为期权
     */
    private void validateOptionType(FxTradeMaster master) {
        if (!TradeType.OPTION.name().equals(master.getTradeType())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "仅期权交易支持此操作");
        }
    }

    /**
     * 校验交易状态为生效或期权费已结清
     */
    private void validateActiveOrPremiumSettled(FxTradeMaster master) {
        String status = master.getStatus();
        if (!TradeStatus.ACTIVE.name().equals(status)
                && !TradeStatus.PREMIUM_SETTLED.name().equals(status)) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "交易当前状态不允许此操作，需为生效或期权费已结清状态");
        }
    }

    /**
     * 校验行权时机：欧式期权需到期日及以后方可行权，美式期权可随时行权
     */
    private void validateExerciseTiming(FxOptionTrade option, LocalDate exerciseDate) {
        if (OptionStyle.EUROPEAN.name().equals(option.getOptionStyle())) {
            if (option.getMaturityDate() != null && exerciseDate.isBefore(option.getMaturityDate())) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "欧式期权需到期日及以后方可行权");
            }
        }
    }

    /**
     * 计算净额交割金额：CALL 时 (参考汇率 - 执行价) * 名义金额，PUT 时 (执行价 - 参考汇率) * 名义金额
     */
    private BigDecimal calculateNetSettlement(FxOptionTrade option) {
        BigDecimal referenceRate = option.getReferenceRate();
        BigDecimal strikePrice = option.getStrikePrice();
        BigDecimal notionalAmount = option.getNotionalAmount();
        if (referenceRate == null || strikePrice == null || notionalAmount == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal diff;
        if ("CALL".equals(option.getOptionType())) {
            diff = referenceRate.subtract(strikePrice);
        } else {
            diff = strikePrice.subtract(referenceRate);
        }
        return diff.multiply(notionalAmount);
    }

    /**
     * 创建生命周期复核任务，指派给交易所属机构的复核角色
     */
    private void createLifecycleCheckTask(FxTradeMaster master, Long currentUserId) {
        String branchCode = master.getBranchCode();
        Integer orgLevel = null;
        if (branchCode != null) {
            FxOrg org = fxOrgMapper.selectByOrgCode(branchCode);
            if (org != null) {
                orgLevel = org.getOrgLevel();
            }
        }
        taskService.createTask(TaskType.CHECK_LIFECYCLE.name(), master.getTradeId(),
                master.getBusinessNo(), master.getTradeType(),
                RoleCode.CHECKER.name(), branchCode, orgLevel, null);
    }
}
