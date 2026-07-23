package com.xfunds.service.impl;

import com.xfunds.common.BusinessException;
import com.xfunds.common.Constants;
import com.xfunds.common.ResultCode;
import com.xfunds.common.SecurityUtils;
import com.xfunds.dto.OptionAbandonRequest;
import com.xfunds.dto.OptionExerciseRequest;
import com.xfunds.dto.OptionPremiumSettleRequest;
import com.xfunds.dto.OptionReminderVO;
import com.xfunds.dto.OptionTradeDetailVO;
import com.xfunds.dto.OptionTradeEntryRequest;
import com.xfunds.dto.PageResponse;
import com.xfunds.entity.FxCustomer;
import com.xfunds.entity.FxCustomerAccount;
import com.xfunds.entity.FxOptionTrade;
import com.xfunds.entity.FxOrg;
import com.xfunds.entity.FxQuote;
import com.xfunds.entity.FxTradeMaster;
import com.xfunds.enums.OptionStyle;
import com.xfunds.enums.RoleCode;
import com.xfunds.enums.SpecialTradeType;
import com.xfunds.enums.TaskType;
import com.xfunds.enums.TradeStatus;
import com.xfunds.enums.TradeType;
import com.xfunds.mapper.FxCustomerAccountMapper;
import com.xfunds.mapper.FxCustomerMapper;
import com.xfunds.mapper.FxOptionTradeMapper;
import com.xfunds.mapper.FxOrgMapper;
import com.xfunds.mapper.FxQuoteMapper;
import com.xfunds.mapper.FxTradeMasterMapper;
import com.xfunds.service.ApprovalLogService;
import com.xfunds.service.MarginService;
import com.xfunds.service.OptionTradeService;
import com.xfunds.service.TaskService;
import com.xfunds.service.TradeLifecycleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 期权交易服务实现类
 * 处理期权交易录入、工作台提醒、行权、放弃、期权费交割等全生命周期操作
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
    private FxCustomerAccountMapper fxCustomerAccountMapper;

    @Autowired
    private FxOrgMapper fxOrgMapper;

    @Autowired
    private FxQuoteMapper fxQuoteMapper;

    @Autowired
    private TradeLifecycleService tradeLifecycleService;

    @Autowired
    private ApprovalLogService approvalLogService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private MarginService marginService;

    /** JSON序列化工具，用于行权参数在任务payload中的存取 */
    @Autowired
    private ObjectMapper objectMapper;

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

        // 提交时余额校验：
        // 1. 校验币种1账户余额是否足够支付面值（复核通过后会冻结该金额）
        validateAccountBalance(req.getCurrency1Account(), req.getNotionalAmount(), "币种1账户面值");
        // 2. 校验期权费账户余额是否足够支付期权费（复核通过后会扣除期权费）
        if (req.getPremiumAmount() != null && req.getPremiumAmount().compareTo(BigDecimal.ZERO) > 0) {
            validateAccountBalance(req.getPremiumAccountId(), req.getPremiumAmount(), "期权费账户期权费");
        }

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
     * 期权交易复核通过处理：
     * 1. 冻结币种1账户的面值金额（frozenAmount += faceValue）
     * 2. 扣除期权费账户的期权费（balance -= premiumAmount）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleOptionApproval(FxTradeMaster master, Long operatorId) {
        FxOptionTrade option = fxOptionTradeMapper.selectByTradeId(master.getTradeId());
        if (option == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "期权交易明细不存在：" + master.getTradeId());
        }

        // 冻结币种1账户的面值金额（复核通过后冻结，行权时扣除，放弃时释放）
        BigDecimal faceValue = option.getNotionalAmount();
        if (faceValue != null && faceValue.compareTo(BigDecimal.ZERO) > 0 && option.getCurrency1Account() != null) {
            freezeAmount(option.getCurrency1Account(), faceValue, master.getTradeId(), operatorId, "复核通过冻结面值");
        }

        // 扣除期权费账户的期权费（复核通过后直接扣除）
        BigDecimal premium = option.getPremiumAmount();
        if (premium != null && premium.compareTo(BigDecimal.ZERO) > 0 && option.getPremiumAccountId() != null) {
            FxCustomerAccount premiumAccount = lookupAccount(option.getPremiumAccountId());
            BigDecimal balance = premiumAccount.getBalance() == null ? BigDecimal.ZERO : premiumAccount.getBalance();
            if (balance.compareTo(premium) < 0) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR,
                        "期权费账户余额不足：余额 " + balance + "，需扣除 " + premium);
            }
            premiumAccount.setBalance(balance.subtract(premium));
            fxCustomerAccountMapper.update(premiumAccount);
        }

        // 更新期权子表期权费支付标志
        option.setPremiumPaidFlag("1");
        fxOptionTradeMapper.update(option);
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
     * 分页查询期权交易列表：先查期权子表获取交易ID列表，再查主表分页，合并期权子表字段返回
     */
    @Override
    public PageResponse<Map<String, Object>> listOptions(String businessNo, String customerId, String optionStyle,
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
        return new PageResponse<>(total, pageNum, pageSize, enrichWithOptions(list));
    }

    /**
     * 查询期权费已交割的期权交易列表（premium_paid_flag = 1）
     */
    @Override
    public PageResponse<Map<String, Object>> listPremiumTrades(String businessNo, String customerId,
                                                         int pageNum, int pageSize) {
        return listOptionsByOptionFlag(businessNo, customerId, pageNum, pageSize,
                Map.of("premiumPaidFlag", "1"));
    }

    /**
     * 查询已行权的期权交易列表（exercise_flag = 1）
     */
    @Override
    public PageResponse<Map<String, Object>> listExerciseTrades(String businessNo, String customerId,
                                                          int pageNum, int pageSize) {
        return listOptionsByOptionFlag(businessNo, customerId, pageNum, pageSize,
                Map.of("exerciseFlag", "1"));
    }

    /**
     * 查询已放弃的期权交易列表（abandon_flag = 1）
     */
    @Override
    public PageResponse<Map<String, Object>> listAbandonTrades(String businessNo, String customerId,
                                                         int pageNum, int pageSize) {
        return listOptionsByOptionFlag(businessNo, customerId, pageNum, pageSize,
                Map.of("abandonFlag", "1"));
    }

    // ==================== 期权工作台 ====================

    /**
     * 查询期权价内提醒列表：
     * 1. 查询所有处于实值状态（CALL: 参考汇率 > 执行价；PUT: 参考汇率 < 执行价）的期权交易（不限美式/欧式）
     * 2. 关联主表过滤状态为生效或期权费已结清
     * 3. 组装提醒视图返回（仅展示，不可在此执行）
     */
    @Override
    public List<OptionReminderVO> getInMoneyReminders() {
        // 查询所有实值期权交易（不限期权类别）
        List<FxOptionTrade> inMoneyOptions = fxOptionTradeMapper.selectAllInMoney();
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
     * 行权（审批制）：
     * 1. 校验交易为期权类型且状态为生效
     * 2. 美式期权到期前可随时行权，欧式期权需到期日及以后行权
     * 3. 仅记录生命周期事件（申请提交），不扣款、不改变交易状态、不设置行权标志
     * 4. 将行权参数（参考汇率、交割账户等）序列化为JSON存入任务payload，创建生命周期复核任务
     * 实际扣款、设置行权标志与行权日、状态变更在 processExerciseApproval 中执行
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void executeOption(String tradeId, OptionExerciseRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        FxTradeMaster master = getTradeOrThrow(tradeId);
        validateOptionType(master);
        validateActive(master);

        FxOptionTrade option = fxOptionTradeMapper.selectByTradeId(tradeId);
        if (option == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "期权交易明细不存在：" + tradeId);
        }

        // 行权时机校验：欧式/美式期权有不同规则
        validateExerciseTiming(option);

        // 审批制：提交时不扣款、不改变状态，仅记录申请提交事件
        String beforeStatus = master.getStatus();
        tradeLifecycleService.recordEvent(tradeId, "EXERCISE", currentUserId,
                beforeStatus, beforeStatus, option.getNotionalAmount(), request.getReferenceRate(),
                null, "期权行权申请提交：" + request.getRemark());

        // 将行权参数序列化为JSON存入任务payload，供审批通过时使用
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "序列化行权参数失败：" + e.getMessage());
        }
        // 创建生命周期复核任务，等待复核通过后执行实际扣款与状态变更
        createLifecycleCheckTask(master, currentUserId, "EXERCISE", payloadJson);
    }

    /**
     * 执行期权审批通过处理：
     * 1. 从payload中解析行权参数（参考汇率、交割账户等）
     * 2. 从币种1账户扣除冻结的面值金额（balance -= faceValue, frozenAmount -= faceValue）
     * 3. 更新期权子表行权标志、行权日（审批通过日）、参考汇率
     * 4. 主表状态变更为已行权
     * 5. 若交割方式为净额交割，计算净额交割金额
     * 6. 记录生命周期事件（复核通过）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processExerciseApproval(String tradeId, Long operatorId, String comment, String payloadJson) {
        FxTradeMaster master = getTradeOrThrow(tradeId);
        validateOptionType(master);

        FxOptionTrade option = fxOptionTradeMapper.selectByTradeId(tradeId);
        if (option == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "期权交易明细不存在：" + tradeId);
        }

        // 从payload中解析行权参数
        OptionExerciseRequest request;
        try {
            request = objectMapper.readValue(payloadJson, OptionExerciseRequest.class);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "解析行权参数失败：" + e.getMessage());
        }

        String beforeStatus = master.getStatus();
        String afterStatus = TradeStatus.EXERCISED.name();

        // 扣款：从币种1账户扣除冻结的面值金额（balance -= faceValue, frozenAmount -= faceValue）
        BigDecimal faceValue = option.getNotionalAmount();
        if (faceValue != null && faceValue.compareTo(BigDecimal.ZERO) > 0) {
            deductFrozenAmount(option.getCurrency1Account(), faceValue, tradeId, operatorId, "期权行权扣款");
        }

        // 更新期权子表：行权标志、行权日（审批通过日）、参考汇率
        option.setExerciseFlag("1");
        option.setExerciseDate(LocalDate.now());
        option.setReferenceRate(request.getReferenceRate());
        if (request.getSettlementAccount() != null) {
            option.setPremiumAccountId(request.getSettlementAccount());
        }
        fxOptionTradeMapper.update(option);

        // 更新主表状态
        master.setStatus(afterStatus);
        fxTradeMasterMapper.update(master);

        // 记录生命周期事件：审批通过
        tradeLifecycleService.recordEvent(tradeId, "EXERCISE", operatorId,
                beforeStatus, afterStatus, option.getNotionalAmount(), request.getReferenceRate(),
                null, "期权行权复核通过：" + (comment != null ? comment : ""));

        // 净额交割时计算净额交割金额：CALL 时 (参考汇率 - 执行价) * 名义金额
        if ("NET".equals(option.getSettlementMethod())) {
            BigDecimal netSettlement = calculateNetSettlement(option);
            tradeLifecycleService.recordEvent(tradeId, "EXERCISE_NET", operatorId,
                    afterStatus, afterStatus, netSettlement, request.getReferenceRate(),
                    null, "期权行权净额交割计算");
        }
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
     * 分页查询欧式期权交易（状态为生效且到期日在今天之后）
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
        params.put("status", TradeStatus.ACTIVE.name());
        params.put("tradeIdList", tradeIdList);
        params.put("maturityDateAfter", LocalDate.now());
        params.put("offset", (pageNum - 1) * pageSize);
        params.put("pageSize", pageSize);

        long total = fxTradeMasterMapper.countByCondition(params);
        List<FxTradeMaster> list = fxTradeMasterMapper.selectByCondition(params);
        return new PageResponse<>(total, pageNum, pageSize, list);
    }

    /**
     * 放弃期权（审批制）：
     * 1. 校验交易为期权类型且状态为生效或期权费已结清
     * 2. 仅记录生命周期事件（申请提交），不改变交易状态、不解冻账户余额
     * 3. 创建生命周期复核任务，等待复核通过后执行实际解冻与状态变更
     * 实际解冻、设置放弃标志与放弃日、状态变更在 processAbandonApproval 中执行
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void abandonOption(String tradeId, OptionAbandonRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        FxTradeMaster master = getTradeOrThrow(tradeId);
        validateOptionType(master);
        validateActive(master);

        FxOptionTrade option = fxOptionTradeMapper.selectByTradeId(tradeId);
        if (option == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "期权交易明细不存在：" + tradeId);
        }

        // 审批制：提交时不解冻、不改变状态，仅记录申请提交事件
        String beforeStatus = master.getStatus();
        tradeLifecycleService.recordEvent(tradeId, "ABANDON", currentUserId,
                beforeStatus, beforeStatus, option.getNotionalAmount(), option.getStrikePrice(),
                null, "期权放弃申请提交：" + request.getRemark());

        // 创建生命周期复核任务，等待复核通过后执行实际解冻与状态变更
        createLifecycleCheckTask(master, currentUserId, "ABANDON");
    }

    /**
     * 放弃期权审批通过处理：
     * 1. 释放币种1账户冻结的面值金额（frozenAmount -= faceValue，余额不变）
     * 2. 更新期权子表放弃标志与放弃日（审批通过日期）
     * 3. 主表状态置为已放弃
     * 4. 记录生命周期事件
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processAbandonApproval(String tradeId, Long operatorId, String comment) {
        FxTradeMaster master = getTradeOrThrow(tradeId);
        validateOptionType(master);

        FxOptionTrade option = fxOptionTradeMapper.selectByTradeId(tradeId);
        if (option == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "期权交易明细不存在：" + tradeId);
        }

        // 释放冻结：归还币种1账户冻结的面值金额（frozenAmount -= faceValue，余额不变）
        BigDecimal faceValue = option.getNotionalAmount();
        if (faceValue != null && faceValue.compareTo(BigDecimal.ZERO) > 0) {
            releaseFrozenAmount(option.getCurrency1Account(), faceValue, tradeId, operatorId, "期权放弃解冻");
        }

        // 更新期权子表放弃标志与放弃日（审批通过日期）
        option.setAbandonFlag("1");
        option.setAbandonDate(LocalDate.now());
        fxOptionTradeMapper.update(option);

        // 主表状态置为已放弃
        String beforeStatus = master.getStatus();
        String afterStatus = TradeStatus.ABANDONED.name();
        master.setStatus(afterStatus);
        fxTradeMasterMapper.update(master);

        // 记录生命周期事件：审批通过
        tradeLifecycleService.recordEvent(tradeId, "ABANDON", operatorId,
                beforeStatus, afterStatus, option.getNotionalAmount(), option.getStrikePrice(),
                null, "期权放弃复核通过：" + (comment != null ? comment : ""));
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
        createLifecycleCheckTask(master, currentUserId, "PREMIUM_SETTLE");
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
     * 分页查询美式期权交易（状态为生效且到期日在今天之后）
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
        params.put("status", TradeStatus.ACTIVE.name());
        params.put("tradeIdList", tradeIdList);
        params.put("maturityDateAfter", LocalDate.now());
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
     * 根据期权子表标志条件分页查询期权交易主表，并合并期权子表字段
     *
     * @param businessNo   业务编号
     * @param customerId   客户ID
     * @param pageNum      页码
     * @param pageSize     每页大小
     * @param optionFlags  期权子表过滤条件（如 exerciseFlag、abandonFlag 等）
     * @return 分页结果（主表 + 期权子表字段合并）
     */
    private PageResponse<Map<String, Object>> listOptionsByOptionFlag(String businessNo, String customerId,
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
        return new PageResponse<>(total, pageNum, pageSize, enrichWithOptions(list));
    }

    /**
     * 批量查询期权子表数据并合并到主表 Map 列表中，供列表查询接口返回完整字段
     * 合并字段包括：买卖方向、期权类别、期权种类、涨跌方向、执行价格、期权费等
     */
    private List<Map<String, Object>> enrichWithOptions(List<FxTradeMaster> list) {
        if (list.isEmpty()) {
            return new ArrayList<>();
        }
        // 收集所有交易ID
        List<String> tradeIds = list.stream()
                .map(FxTradeMaster::getTradeId)
                .collect(Collectors.toList());
        // 批量查询期权子表数据
        Map<String, Object> optionParams = new HashMap<>();
        optionParams.put("tradeIdList", tradeIds);
        optionParams.put("offset", 0);
        optionParams.put("pageSize", Integer.MAX_VALUE);
        List<FxOptionTrade> options = fxOptionTradeMapper.selectByCondition(optionParams);
        // 转为 tradeId -> FxOptionTrade 映射
        Map<String, FxOptionTrade> optionMap = options.stream()
                .collect(Collectors.toMap(FxOptionTrade::getTradeId, o -> o, (a, b) -> a));

        // 合并主表与期权子表字段
        List<Map<String, Object>> result = new ArrayList<>();
        for (FxTradeMaster master : list) {
            Map<String, Object> row = convertMasterToMap(master);
            FxOptionTrade option = optionMap.get(master.getTradeId());
            if (option != null) {
                row.put("buyerSeller", option.getBuyerSeller());
                row.put("optionStyle", option.getOptionStyle());
                row.put("optionType", option.getOptionType());
                // 涨跌方向根据期权类型推断：CALL 为涨，PUT 为跌
                row.put("priceDirection", "CALL".equals(option.getOptionType()) ? "UP" : "DOWN");
                row.put("strikePrice", option.getStrikePrice());
                row.put("premiumAmount", option.getPremiumAmount());
                row.put("premiumCurrency", option.getPremiumCurrency());
                row.put("premiumValueDate", option.getPremiumValueDate());
                row.put("premiumPaidFlag", option.getPremiumPaidFlag());
                row.put("exerciseFlag", option.getExerciseFlag());
                row.put("abandonFlag", option.getAbandonFlag());
                row.put("abandonDate", option.getAbandonDate());
                row.put("exerciseDate", option.getExerciseDate());
                row.put("observationStartDate", option.getObservationStartDate());
                row.put("observationEndDate", option.getObservationEndDate());
                row.put("closeDate", option.getCloseDate());
                row.put("referenceRate", option.getReferenceRate());
                row.put("remainingAmount", option.getRemainingAmount());
            }
            result.add(row);
        }
        return result;
    }

    /**
     * 将交易主表对象转换为 Map（包含主表所有字段），用于列表接口返回
     */
    private Map<String, Object> convertMasterToMap(FxTradeMaster master) {
        Map<String, Object> row = new HashMap<>();
        row.put("tradeId", master.getTradeId());
        row.put("businessNo", master.getBusinessNo());
        row.put("tradeType", master.getTradeType());
        row.put("status", master.getStatus());
        row.put("branchCode", master.getBranchCode());
        row.put("branchName", master.getBranchName());
        row.put("customerId", master.getCustomerId());
        row.put("customerName", master.getCustomerName());
        row.put("baseCurrency", master.getBaseCurrency());
        row.put("quoteCurrency", master.getQuoteCurrency());
        row.put("currencyPair", master.getCurrencyPair());
        row.put("notionalAmount", master.getNotionalAmount());
        row.put("counterAmount", master.getCounterAmount());
        row.put("tradeDirection", master.getTradeDirection());
        row.put("valueDate", master.getValueDate());
        row.put("tradeDate", master.getTradeDate());
        row.put("maturityDate", master.getMaturityDate());
        row.put("deliveryType", master.getDeliveryType());
        row.put("settlementMethod", master.getSettlementMethod());
        row.put("spotRate", master.getSpotRate());
        row.put("customerRate", master.getCustomerRate());
        row.put("costRate", master.getCostRate());
        row.put("branchProfitPoint", master.getBranchProfitPoint());
        row.put("specialTradeType", master.getSpecialTradeType());
        row.put("originalTradeId", master.getOriginalTradeId());
        row.put("originalTradeType", master.getOriginalTradeType());
        row.put("relatedTradeId", master.getRelatedTradeId());
        row.put("nettingCurrency", master.getNettingCurrency());
        row.put("nettingAccount", master.getNettingAccount());
        row.put("nettingAmount", master.getNettingAmount());
        row.put("makerId", master.getMakerId());
        row.put("checkerId", master.getCheckerId());
        row.put("authorizerId", master.getAuthorizerId());
        row.put("makeTime", master.getMakeTime());
        row.put("checkTime", master.getCheckTime());
        row.put("authorizeTime", master.getAuthorizeTime());
        row.put("purposeCode", master.getPurposeCode());
        row.put("rcpmisReportFlag", master.getRcpmisReportFlag());
        row.put("rcpmisReportTime", master.getRcpmisReportTime());
        row.put("version", master.getVersion());
        row.put("createdAt", master.getCreatedAt());
        row.put("updatedAt", master.getUpdatedAt());
        return row;
    }

    /**
     * 牌价刷新时更新期权参考汇率：遍历所有有效期权交易，根据货币对查找当前报价中间价，
     * 更新 fx_option_trade.reference_rate 字段，用于价内提醒监听
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateReferenceRates() {
        // 查询所有有效SPOT即期报价，构建 currencyPair -> 卖出价/买入价 映射
        // CALL期权(看涨): 参考汇率为总行卖出价(客户买入价), 卖出价 > 执行价时价内
        // PUT期权(看跌): 参考汇率为总行买入价(客户卖出价), 买入价 < 执行价时价内
        List<FxQuote> quotes = fxQuoteMapper.selectAllActive();
        Map<String, BigDecimal> sellRateMap = new HashMap<>();
        Map<String, BigDecimal> buyRateMap = new HashMap<>();
        for (FxQuote quote : quotes) {
            if (quote.getCurrencyPair() == null) {
                continue;
            }
            // 仅取即期(SPOT)报价，避免远期/掉期报价干扰
            if (!"SPOT".equals(quote.getQuoteType())) {
                continue;
            }
            BigDecimal buy = quote.getTotalBuyRate();
            BigDecimal sell = quote.getTotalSellRate();
            if (buy != null && buy.compareTo(BigDecimal.ZERO) > 0) {
                buyRateMap.put(quote.getCurrencyPair(), buy);
            }
            if (sell != null && sell.compareTo(BigDecimal.ZERO) > 0) {
                sellRateMap.put(quote.getCurrencyPair(), sell);
            }
        }

        // 查询所有未行权、未放弃的期权交易
        Map<String, Object> optionParams = new HashMap<>();
        optionParams.put("exerciseFlag", "0");
        optionParams.put("abandonFlag", "0");
        optionParams.put("offset", 0);
        optionParams.put("pageSize", Integer.MAX_VALUE);
        List<FxOptionTrade> activeOptions = fxOptionTradeMapper.selectByCondition(optionParams);

        // 遍历更新参考汇率：CALL用卖出价，PUT用买入价
        for (FxOptionTrade option : activeOptions) {
            FxTradeMaster master = fxTradeMasterMapper.selectByTradeId(option.getTradeId());
            if (master == null || master.getCurrencyPair() == null) {
                continue;
            }
            // 仅更新生效或期权费已结清状态的交易
            String status = master.getStatus();
            if (!TradeStatus.ACTIVE.name().equals(status)
                    && !TradeStatus.PREMIUM_SETTLED.name().equals(status)) {
                continue;
            }
            // 参考汇率取即期汇率中间价 = (总行买入价 + 总行卖出价) / 2
            BigDecimal buyRate = buyRateMap.get(master.getCurrencyPair());
            BigDecimal sellRate = sellRateMap.get(master.getCurrencyPair());
            BigDecimal refRate = null;
            if (buyRate != null && sellRate != null) {
                refRate = buyRate.add(sellRate).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
            }
            if (refRate != null && (option.getReferenceRate() == null
                    || option.getReferenceRate().compareTo(refRate) != 0)) {
                option.setReferenceRate(refRate);
                fxOptionTradeMapper.update(option);
            }
        }
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
        vo.setMaturityDate(master.getMaturityDate());
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
     * 校验交易状态为生效
     */
    private void validateActive(FxTradeMaster master) {
        if (!TradeStatus.ACTIVE.name().equals(master.getStatus())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "交易当前状态不允许此操作，需为生效状态");
        }
    }

    /**
     * 校验行权时机：
     * - 欧式期权：到期日前点执行→"欧式期权未到执行日"；到期日当天但15:00前→"欧式期权未到行权时点"
     * - 美式期权：到期日前15:00后可执行；15:00前→"美式期权未到行权时点"
     */
    private void validateExerciseTiming(FxOptionTrade option) {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        // 行权时点固定为15:00
        LocalTime exerciseTime = LocalTime.of(15, 0);

        if (OptionStyle.EUROPEAN.name().equals(option.getOptionStyle())) {
            // 欧式期权：到期日前不可执行
            if (option.getMaturityDate() != null && today.isBefore(option.getMaturityDate())) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "欧式期权未到执行日");
            }
            // 到期日当天但15:00前不可执行
            if (option.getMaturityDate() != null && today.isEqual(option.getMaturityDate()) && now.isBefore(exerciseTime)) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "欧式期权未到行权时点");
            }
        } else if (OptionStyle.AMERICAN.name().equals(option.getOptionStyle())) {
            // 美式期权：到期日前15:00后可执行，15:00前不可执行
            if (option.getMaturityDate() != null && today.isBefore(option.getMaturityDate()) && now.isBefore(exerciseTime)) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "美式期权未到行权时点");
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
     * 创建生命周期复核任务（无payload），指派给交易所属机构的复核角色
     * 提交前校验：若该交易已存在待处理的同类型生命周期任务，则拒绝重复提交
     * @param master 交易主表
     * @param currentUserId 操作人
     * @param businessType 业务类型（ABANDON/EXERCISE/PREMIUM_SETTLE/POSTPONE，用于区分生命周期操作类型）
     */
    private void createLifecycleCheckTask(FxTradeMaster master, Long currentUserId, String businessType) {
        createLifecycleCheckTask(master, currentUserId, businessType, null);
    }

    /**
     * 创建生命周期复核任务（可携带payload），指派给交易所属机构的复核角色
     * 提交前校验：若该交易已存在待处理的同类型生命周期任务，则拒绝重复提交
     * @param master 交易主表
     * @param currentUserId 操作人
     * @param businessType 业务类型（ABANDON/EXERCISE/PREMIUM_SETTLE/POSTPONE，用于区分生命周期操作类型）
     * @param payload 任务载荷JSON（如行权参数），可为null
     */
    private void createLifecycleCheckTask(FxTradeMaster master, Long currentUserId, String businessType, String payload) {
        // 防止审批期间重复提交：若已存在待处理的同类型任务，拒绝再次提交
        if (taskService.hasPendingLifecycleTask(master.getTradeId(), businessType)) {
            String opName = "ABANDON".equals(businessType) ? "放弃"
                    : "EXERCISE".equals(businessType) ? "执行"
                    : "PREMIUM_SETTLE".equals(businessType) ? "期权费交割" : "生命周期操作";
            throw new BusinessException(ResultCode.BUSINESS_ERROR,
                    "该交易已存在待复核的" + opName + "申请，请等待复核完成后再操作");
        }
        String branchCode = master.getBranchCode();
        Integer orgLevel = null;
        if (branchCode != null) {
            FxOrg org = fxOrgMapper.selectByOrgCode(branchCode);
            if (org != null) {
                orgLevel = org.getOrgLevel();
            }
        }
        taskService.createTask(TaskType.CHECK_LIFECYCLE.name(), master.getTradeId(),
                master.getBusinessNo(), businessType,
                RoleCode.CHECKER.name(), branchCode, orgLevel, payload);
    }

    // ==================== 账户余额校验与冻结/扣款/解冻 ====================

    /**
     * 校验账户余额是否足够：根据账户ID字符串查询账户，校验可用余额（balance - frozenAmount）是否 >= 所需金额
     *
     * @param accountIdStr 账户ID字符串
     * @param requiredAmount 所需金额
     * @param desc 描述（用于错误提示）
     */
    private void validateAccountBalance(String accountIdStr, BigDecimal requiredAmount, String desc) {
        if (accountIdStr == null || accountIdStr.isEmpty()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, desc + "：账户未选择");
        }
        if (requiredAmount == null || requiredAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, desc + "：金额必须大于0");
        }
        FxCustomerAccount account = lookupAccount(accountIdStr);
        BigDecimal balance = account.getBalance() == null ? BigDecimal.ZERO : account.getBalance();
        if (balance.compareTo(requiredAmount) < 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR,
                    desc + "不足：账户余额 " + balance + "，需要 " + requiredAmount);
        }
    }

    /**
     * 根据账户ID或账户号字符串查询账户，不存在则抛出异常
     * 兼容两种存储格式：先按账户号查询，若未找到则尝试按账户ID查询
     */
    private FxCustomerAccount lookupAccount(String accountIdStr) {
        if (accountIdStr == null || accountIdStr.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "账户未选择");
        }
        // 先按账户号查询（新数据使用 accountNo 作为 currency1Account 的值）
        FxCustomerAccount account = fxCustomerAccountMapper.selectByAccountNo(accountIdStr);
        if (account != null) {
            return account;
        }
        // 再尝试按账户ID查询（旧数据可能使用 accountId 作为值）
        try {
            Long accountId = Long.parseLong(accountIdStr);
            account = fxCustomerAccountMapper.selectByAccountId(accountId);
            if (account != null) {
                return account;
            }
        } catch (NumberFormatException ignored) {
            // 非数字格式，跳过 accountId 查询
        }
        throw new BusinessException(ResultCode.NOT_FOUND, "账户不存在：" + accountIdStr);
    }

    /**
     * 冻结账户金额：frozenAmount += amount（余额不变，仅增加冻结）
     */
    private void freezeAmount(String accountIdStr, BigDecimal amount, String tradeId, Long operatorId, String remark) {
        FxCustomerAccount account = lookupAccount(accountIdStr);
        BigDecimal frozen = account.getFrozenAmount() == null ? BigDecimal.ZERO : account.getFrozenAmount();
        account.setFrozenAmount(frozen.add(amount));
        fxCustomerAccountMapper.update(account);
    }

    /**
     * 扣除冻结金额：balance -= amount, frozenAmount -= amount（行权时实际扣款）
     */
    private void deductFrozenAmount(String accountIdStr, BigDecimal amount, String tradeId, Long operatorId, String remark) {
        FxCustomerAccount account = lookupAccount(accountIdStr);
        BigDecimal balance = account.getBalance() == null ? BigDecimal.ZERO : account.getBalance();
        BigDecimal frozen = account.getFrozenAmount() == null ? BigDecimal.ZERO : account.getFrozenAmount();
        if (frozen.compareTo(amount) < 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR,
                    "冻结金额不足：已冻结 " + frozen + "，需扣除 " + amount);
        }
        account.setBalance(balance.subtract(amount));
        account.setFrozenAmount(frozen.subtract(amount));
        fxCustomerAccountMapper.update(account);
    }

    /**
     * 释放冻结金额：frozenAmount -= amount（放弃时解冻，余额不变）
     */
    private void releaseFrozenAmount(String accountIdStr, BigDecimal amount, String tradeId, Long operatorId, String remark) {
        FxCustomerAccount account = lookupAccount(accountIdStr);
        BigDecimal frozen = account.getFrozenAmount() == null ? BigDecimal.ZERO : account.getFrozenAmount();
        if (frozen.compareTo(amount) < 0) {
            // 冻结金额不足时仅释放已有的
            amount = frozen;
        }
        account.setFrozenAmount(frozen.subtract(amount));
        fxCustomerAccountMapper.update(account);
    }
}
