package com.xfunds.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xfunds.common.BusinessException;
import com.xfunds.common.Constants;
import com.xfunds.common.ResultCode;
import com.xfunds.common.SecurityUtils;
import com.xfunds.dto.EarlyDefaultRequest;
import com.xfunds.dto.EarlyDefaultTaskPayload;
import com.xfunds.dto.EarlyDeliveryRequest;
import com.xfunds.dto.EarlyDeliveryTaskPayload;
import com.xfunds.dto.FullDefaultRequest;
import com.xfunds.dto.MarginSupplementRequest;
import com.xfunds.dto.RolloverMarketRequest;
import com.xfunds.dto.RolloverRequest;
import com.xfunds.entity.FxForwardTrade;
import com.xfunds.entity.FxMarginAccount;
import com.xfunds.entity.FxOrg;
import com.xfunds.entity.FxSpotTrade;
import com.xfunds.entity.FxSwapTrade;
import com.xfunds.entity.FxTask;
import com.xfunds.entity.FxTradeMaster;
import com.xfunds.entity.FxUser;
import com.xfunds.entity.FxCustomerAccount;
import com.xfunds.enums.RoleCode;
import com.xfunds.enums.SettlementMethod;
import com.xfunds.enums.SpecialTradeType;
import com.xfunds.enums.TaskStatus;
import com.xfunds.enums.TaskType;
import com.xfunds.enums.TradeStatus;
import com.xfunds.enums.TradeType;
import com.xfunds.mapper.FxMarginAccountMapper;
import com.xfunds.mapper.FxForwardTradeMapper;
import com.xfunds.mapper.FxOrgMapper;
import com.xfunds.mapper.FxSpotTradeMapper;
import com.xfunds.mapper.FxSwapTradeMapper;
import com.xfunds.mapper.FxTradeMasterMapper;
import com.xfunds.mapper.FxCustomerAccountMapper;
import com.xfunds.service.ApprovalLogService;
import com.xfunds.service.MarginService;
import com.xfunds.service.TaskService;
import com.xfunds.service.TradeLifecycleOpsService;
import com.xfunds.service.TradeLifecycleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 交易生命周期操作服务实现类
 * 处理审批流程与各类生命周期操作（提前交割/违约/展期/到期处理等），
 * 每个操作均在同一事务内完成状态变更、生命周期记录、审批日志与任务流转
 */
@Service
public class TradeLifecycleOpsServiceImpl implements TradeLifecycleOpsService {

    /** 交易ID时间格式：yyyyMMddHHmmss */
    private static final DateTimeFormatter TRADE_ID_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 业务编号日期格式：yyyyMMdd */
    private static final DateTimeFormatter BIZ_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired
    private FxTradeMasterMapper fxTradeMasterMapper;

    @Autowired
    private FxSpotTradeMapper fxSpotTradeMapper;

    @Autowired
    private FxForwardTradeMapper fxForwardTradeMapper;

    @Autowired
    private FxSwapTradeMapper fxSwapTradeMapper;

    @Autowired
    private FxMarginAccountMapper fxMarginAccountMapper;

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

    @Autowired
    private FxCustomerAccountMapper fxCustomerAccountMapper;

    @Autowired
    private ObjectMapper objectMapper;

    // ==================== 审批流程 ====================

    /**
     * 复核通过：
     * 1. 校验任务存在且状态为待处理/已认领，当前用户具备复核角色
     * 2. 如果是提前交割任务，执行实际提前交割逻辑
     * 3. 如果是提前违约任务，执行实际提前违约逻辑
     * 4. 如果是普通交易，按原逻辑处理
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String approveTrade(Long taskId, String tradeId, String comment) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        FxUser currentUser = SecurityUtils.getCurrentUser();

        // 校验复核权限
        if (!SecurityUtils.hasRole(RoleCode.CHECKER.name())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前用户无复核权限");
        }

        FxTask task = validateTask(taskId, tradeId);

        // 判断任务类型
        if (TaskType.EARLY_DELIVERY.name().equals(task.getTaskType())) {
            // 处理提前交割任务
            return processEarlyDeliveryApproval(task, currentUserId, currentUser, comment);
        }
        if (TaskType.EARLY_DEFAULT.name().equals(task.getTaskType())) {
            // 处理提前违约任务
            return processEarlyDefaultApproval(task, currentUserId, currentUser, comment);
        }

        // 处理普通交易复核
        FxTradeMaster master = getTradeOrThrow(tradeId);

        // 经办人≠复核人校验（仅当用户不同时拥有 MAKER 和 CHECKER 角色时）
        if (master.getMakerId() != null && master.getMakerId().equals(currentUserId) && !SecurityUtils.hasBothMakerAndCheckerRoles()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "经办人与复核人不能为同一人");
        }

        String beforeStatus = master.getStatus();
        String afterStatus;

        // 判断是否需要授权：金额超过机构审批额度时保持待复核并创建授权任务
        boolean needAuthorize = needAuthorization(master);
        if (needAuthorize) {
            afterStatus = TradeStatus.PENDING_CHECK.name();
        } else {
            afterStatus = TradeStatus.ACTIVE.name();
        }

        // 更新交易状态与复核人信息
        master.setCheckerId(currentUserId);
        master.setCheckTime(LocalDateTime.now());
        master.setStatus(afterStatus);
        int updated = fxTradeMasterMapper.update(master);
        if (updated == 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "交易已被他人修改，请刷新后重试");
        }

        // 保证金交易：审批通过进入生效态时扣除保证金（交割成功退还，交割失败没收）
        if (!needAuthorize) {
            deductMarginOnApproval(master, currentUserId);
        }

        // 记录审批日志
        approvalLogService.recordLog(tradeId, Constants.NODE_CHECK, currentUserId,
                currentUser != null ? currentUser.getRealName() : null,
                SecurityUtils.getCurrentOrgCode(), Constants.DECISION_APPROVE, comment, beforeStatus, afterStatus);

        // 记录生命周期事件
        tradeLifecycleService.recordEvent(tradeId, Constants.NODE_CHECK, currentUserId,
                beforeStatus, afterStatus, master.getNotionalAmount(), master.getCustomerRate(),
                null, "复核通过");

        // 完成复核任务
        taskService.completeTask(taskId, currentUserId);

        // 需要授权时创建授权任务，指派给上级机构的授权角色
        if (needAuthorize) {
            createAuthorizeTask(master);
        }

        return tradeId;
    }

    /**
     * 保证金扣除：审批通过进入生效态时扣除保证金
     * 保证金交易规则：审批通过即扣保证金，交割成功退还，交割失败没收
     * 从交易子表（即期/远期/掉期）读取保证金账户与金额；无保证金或金额<=0时跳过
     */
    private void deductMarginOnApproval(FxTradeMaster master, Long operatorId) {
        String marginAccountId = null;
        BigDecimal marginAmount = null;
        String tradeType = master.getTradeType();
        if (TradeType.SPOT.name().equals(tradeType)) {
            FxSpotTrade spot = fxSpotTradeMapper.selectByTradeId(master.getTradeId());
            if (spot != null) {
                marginAccountId = spot.getMarginAccountId();
                marginAmount = spot.getMarginAmount();
            }
        } else if (TradeType.FORWARD.name().equals(tradeType)) {
            FxForwardTrade forward = fxForwardTradeMapper.selectByTradeId(master.getTradeId());
            if (forward != null) {
                marginAccountId = forward.getMarginAccountId();
                marginAmount = forward.getMarginAmount();
            }
        } else if (TradeType.SWAP.name().equals(tradeType)) {
            FxSwapTrade swap = fxSwapTradeMapper.selectByTradeId(master.getTradeId());
            if (swap != null) {
                marginAccountId = swap.getMarginAccountId();
                marginAmount = swap.getMarginAmount();
            }
        }
        // 无保证金账户或保证金金额<=0时跳过（如期权或0保证金交易）
        if (marginAccountId == null || marginAccountId.isEmpty()
                || marginAmount == null || marginAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        marginService.deduct(marginAccountId, marginAmount, master.getTradeId(), operatorId, "审批通过扣除保证金");
    }

    /**
     * 处理提前交割任务的复核通过
     */
    private String processEarlyDeliveryApproval(FxTask task, Long currentUserId, FxUser currentUser, String comment) {
        try {
            // 解析任务载荷
            EarlyDeliveryTaskPayload payload = objectMapper.readValue(task.getPayload(), EarlyDeliveryTaskPayload.class);
            
            // 获取原交易
            FxTradeMaster original = getTradeOrThrow(payload.getOriginalTradeId());
            String beforeStatus = original.getStatus();
            String afterStatus = TradeStatus.EARLY_SETTLED.name();

            // 更新原交易状态
            original.setStatus(afterStatus);
            fxTradeMasterMapper.update(original);

            // 记录原交易生命周期事件
            tradeLifecycleService.recordEvent(original.getTradeId(), "EARLY_DELIVERY", currentUserId,
                    beforeStatus, afterStatus, payload.getOriginalAmount(), payload.getOriginalCustomerRate(),
                    null, "提前交割：" + payload.getRemark());

            // 生成掉期交易
            String swapTradeId = generateSwapForEarlyDelivery(original, payload, currentUserId);

            // 完成提前交割任务
            taskService.completeTask(task.getTaskId(), currentUserId);

            return swapTradeId;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "处理提前交割任务失败：" + e.getMessage());
        }
    }

    /**
     * 处理提前违约任务的复核通过
     */
    private String processEarlyDefaultApproval(FxTask task, Long currentUserId, FxUser currentUser, String comment) {
        try {
            // 解析任务载荷
            EarlyDefaultTaskPayload payload = objectMapper.readValue(task.getPayload(), EarlyDefaultTaskPayload.class);
            
            // 获取原交易
            FxTradeMaster original = getTradeOrThrow(payload.getOriginalTradeId());
            String beforeStatus = original.getStatus();
            String afterStatus = TradeStatus.EARLY_DEFAULTED.name();

            // 更新原交易状态
            original.setStatus(afterStatus);
            fxTradeMasterMapper.update(original);

            // 如果是远期交易，更新子表的 earlyDefaultFlag 和交割方式为无需交割
            if (TradeType.FORWARD.name().equals(original.getTradeType())) {
                var forwardTrade = fxForwardTradeMapper.selectByTradeId(original.getTradeId());
                if (forwardTrade != null) {
                    forwardTrade.setEarlyDefaultFlag(Constants.YES);
                    forwardTrade.setSettlementMethod(SettlementMethod.NONE.name());
                    fxForwardTradeMapper.update(forwardTrade);
                }
            }

            // 更新原交易主表交割方式为无需交割
            fxTradeMasterMapper.updateSettlementMethod(original.getTradeId(), SettlementMethod.NONE.name());

            // 记录原交易生命周期事件
            tradeLifecycleService.recordEvent(original.getTradeId(), "EARLY_DEFAULT", currentUserId,
                    beforeStatus, afterStatus, payload.getDefaultAmount(), payload.getOriginalCustomerRate(),
                    null, "提前违约：" + payload.getRemark());

            // 生成反向即期交易
            String spotTradeId = generateSpotForEarlyDefault(original, payload, currentUserId);

            // 生成掉期交易
            String swapTradeId = generateSwapForEarlyDefault(original, payload, currentUserId);

            // 记录生成交易的生命周期事件
            tradeLifecycleService.recordEvent(spotTradeId, "EARLY_DEFAULT_GEN", currentUserId,
                    null, TradeStatus.ACTIVE.name(), payload.getDefaultAmount(), payload.getSpotCustomerRate(),
                    null, "提前违约生成即期交易（差额交割）");
            tradeLifecycleService.recordEvent(swapTradeId, "EARLY_DEFAULT_GEN", currentUserId,
                    null, TradeStatus.ACTIVE.name(), payload.getDefaultAmount(), payload.getPenaltyRate(),
                    null, "提前违约生成掉期交易（无需交割）");

            // 完成提前违约任务
            taskService.completeTask(task.getTaskId(), currentUserId);

            return spotTradeId;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "处理提前违约任务失败：" + e.getMessage());
        }
    }

    /**
     * 复核拒绝：交易状态置为已拒绝，记录审批日志与生命周期事件，完成任务
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectTrade(Long taskId, String tradeId, String comment) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        FxUser currentUser = SecurityUtils.getCurrentUser();

        if (!SecurityUtils.hasRole(RoleCode.CHECKER.name())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前用户无复核权限");
        }

        FxTask task = validateTask(taskId, tradeId);
        FxTradeMaster master = getTradeOrThrow(tradeId);

        // 经办人≠复核人校验（仅当用户不同时拥有 MAKER 和 CHECKER 角色时）
        if (master.getMakerId() != null && master.getMakerId().equals(currentUserId) && !SecurityUtils.hasBothMakerAndCheckerRoles()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "经办人与复核人不能为同一人");
        }

        // 若为提前违约任务被拒绝，释放冻结的轧差金额
        releaseNettingFreeze(task, master);

        String beforeStatus = master.getStatus();
        String afterStatus = TradeStatus.REJECTED.name();

        master.setCheckerId(currentUserId);
        master.setCheckTime(LocalDateTime.now());
        master.setStatus(afterStatus);
        fxTradeMasterMapper.update(master);

        approvalLogService.recordLog(tradeId, Constants.NODE_CHECK, currentUserId,
                currentUser != null ? currentUser.getRealName() : null,
                SecurityUtils.getCurrentOrgCode(), Constants.DECISION_REJECT, comment, beforeStatus, afterStatus);

        tradeLifecycleService.recordEvent(tradeId, Constants.NODE_CHECK, currentUserId,
                beforeStatus, afterStatus, master.getNotionalAmount(), master.getCustomerRate(),
                null, "复核拒绝");

        taskService.completeTask(taskId, currentUserId);
    }

    /**
     * 退回经办：交易状态回退为草稿，记录审批日志与生命周期事件，完成任务，创建修改任务给原经办人员
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void returnTrade(Long taskId, String tradeId, String comment) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        FxUser currentUser = SecurityUtils.getCurrentUser();

        if (!SecurityUtils.hasRole(RoleCode.CHECKER.name())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前用户无复核权限");
        }

        FxTask task = validateTask(taskId, tradeId);
        FxTradeMaster master = getTradeOrThrow(tradeId);

        // 经办人≠复核人校验（仅当用户不同时拥有 MAKER 和 CHECKER 角色时）
        if (master.getMakerId() != null && master.getMakerId().equals(currentUserId) && !SecurityUtils.hasBothMakerAndCheckerRoles()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "经办人与复核人不能为同一人");
        }

        // 若为提前违约任务被退回，释放冻结的轧差金额
        releaseNettingFreeze(task, master);

        String beforeStatus = master.getStatus();
        String afterStatus = TradeStatus.DRAFT.name();

        master.setCheckerId(currentUserId);
        master.setCheckTime(LocalDateTime.now());
        master.setStatus(afterStatus);
        fxTradeMasterMapper.update(master);

        approvalLogService.recordLog(tradeId, Constants.NODE_CHECK, currentUserId,
                currentUser != null ? currentUser.getRealName() : null,
                SecurityUtils.getCurrentOrgCode(), Constants.DECISION_RETURN, comment, beforeStatus, afterStatus);

        tradeLifecycleService.recordEvent(tradeId, Constants.NODE_CHECK, currentUserId,
                beforeStatus, afterStatus, master.getNotionalAmount(), master.getCustomerRate(),
                null, "退回经办");

        taskService.completeTask(taskId, currentUserId);

        // 创建修改任务给原经办人员
        if (master.getMakerId() != null) {
            // 查询机构层级
            Integer orgLevel = null;
            if (master.getBranchCode() != null) {
                FxOrg org = fxOrgMapper.selectByOrgCode(master.getBranchCode());
                if (org != null) {
                    orgLevel = org.getOrgLevel();
                }
            }
            taskService.createTaskForUser(TaskType.MODIFY.name(), tradeId, master.getBusinessNo(),
                    master.getTradeType(), master.getMakerId(), master.getBranchCode(), orgLevel, null);
        }
    }

    // ==================== 未到期交易操作 ====================

    /**
     * 提前交割：
     * 1. 创建提前交割任务（不立即变更原交易状态、不生成新交易）
     * 2. 任务载荷中存储提前交割相关信息
     * 3. 任务复核通过后才执行实际的提前交割逻辑
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String earlyDelivery(String tradeId, EarlyDeliveryRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        FxTradeMaster original = getTradeOrThrow(tradeId);
        validateActiveStatus(original);
        validateForwardOrSwap(original);

        // 准备任务载荷数据
        EarlyDeliveryTaskPayload payload = new EarlyDeliveryTaskPayload();
        payload.setOriginalTradeId(original.getTradeId());
        payload.setOriginalBusinessNo(original.getBusinessNo());
        payload.setOriginalTradeType(original.getTradeType());
        payload.setCustomerId(original.getCustomerId());
        payload.setCustomerName(original.getCustomerName());
        payload.setCurrencyPair(original.getCurrencyPair());
        payload.setOriginalTradeDirection(original.getTradeDirection());
        payload.setOriginalAmount(original.getNotionalAmount());
        payload.setOriginalCustomerRate(original.getCustomerRate());
        payload.setOriginalMaturityDate(original.getMaturityDate());
        
        payload.setNearLegCustomerRate(request.getNearLegCustomerRate());
        payload.setNearLegCostRate(request.getNearLegCostRate());
        payload.setFarLegCustomerRate(request.getFarLegCustomerRate());
        payload.setFarLegCostRate(request.getFarLegCostRate());
        payload.setNearLegAccount1(request.getNearLegAccount1());
        payload.setNearLegAccount2(request.getNearLegAccount2());
        payload.setNearLegValueDate(request.getNearLegValueDate());
        payload.setFarLegValueDate(request.getFarLegValueDate());
        payload.setRemark(request.getRemark());

        // 序列化载荷为 JSON
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "序列化任务载荷失败");
        }

        // 查询机构信息
        Integer orgLevel = null;
        if (original.getBranchCode() != null) {
            FxOrg org = fxOrgMapper.selectByOrgCode(original.getBranchCode());
            if (org != null) {
                orgLevel = org.getOrgLevel();
            }
        }

        // 创建提前交割任务
        taskService.createTask(
                TaskType.EARLY_DELIVERY.name(),
                original.getTradeId(),
                original.getBusinessNo(),
                "提前交割",
                RoleCode.CHECKER.name(),
                original.getBranchCode(),
                orgLevel,
                payloadJson
        );

        return original.getTradeId();
    }

    /**
     * 提前违约：
     * 1. 创建提前违约任务（不立即变更原交易状态、不生成新交易）
     * 2. 任务载荷中存储提前违约相关信息
     * 3. 任务复核通过后才执行实际的提前违约逻辑
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String earlyDefault(String tradeId, EarlyDefaultRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        FxTradeMaster original = getTradeOrThrow(tradeId);
        validateActiveStatus(original);
        validateForwardOrSwap(original);

        // 冻结轧差账户中的轧差金额（避免提交到交割期间资金被挪用导致划款失败）
        if (request.getNettingAccount() != null && request.getNettingAmount() != null
                && request.getNettingAmount().compareTo(BigDecimal.ZERO) > 0) {
            List<FxCustomerAccount> accounts = fxCustomerAccountMapper.selectByCustomerIdAndCurrency(
                    original.getCustomerId(), request.getNettingCurrency());
            FxCustomerAccount nettingAcc = null;
            if (accounts != null) {
                for (FxCustomerAccount acc : accounts) {
                    if (request.getNettingAccount().equals(acc.getAccountNo())) {
                        nettingAcc = acc;
                        break;
                    }
                }
            }
            if (nettingAcc == null) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "轧差账户不存在");
            }
            BigDecimal balance = nettingAcc.getBalance() != null ? nettingAcc.getBalance() : BigDecimal.ZERO;
            BigDecimal frozen = nettingAcc.getFrozenAmount() != null ? nettingAcc.getFrozenAmount() : BigDecimal.ZERO;
            BigDecimal available = balance.subtract(frozen);
            if (available.compareTo(request.getNettingAmount()) < 0) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR,
                        "轧差账户可用余额不足，可用余额：" + available + "，轧差金额：" + request.getNettingAmount());
            }
            // 冻结轧差金额：frozenAmount += nettingAmount
            nettingAcc.setFrozenAmount(frozen.add(request.getNettingAmount()));
            fxCustomerAccountMapper.update(nettingAcc);
        }

        // 准备任务载荷数据
        EarlyDefaultTaskPayload payload = new EarlyDefaultTaskPayload();
        payload.setOriginalTradeId(original.getTradeId());
        payload.setOriginalBusinessNo(original.getBusinessNo());
        payload.setOriginalTradeType(original.getTradeType());
        payload.setCustomerId(original.getCustomerId());
        payload.setCustomerName(original.getCustomerName());
        payload.setCurrencyPair(original.getCurrencyPair());
        payload.setOriginalTradeDirection(original.getTradeDirection());
        payload.setOriginalAmount(original.getNotionalAmount());
        payload.setOriginalCustomerRate(original.getCustomerRate());
        payload.setOriginalMaturityDate(original.getMaturityDate());
        payload.setDefaultAmount(request.getDefaultAmount());
        payload.setSpotCustomerRate(request.getSpotCustomerRate());
        payload.setSpotCostRate(original.getCostRate());
        payload.setPenaltyRate(request.getPenaltyRate());
        payload.setSwapNearLegValueDate(request.getSwapNearLegValueDate());
        payload.setSwapNearLegRate(request.getSwapNearLegRate());
        payload.setSwapNearLegCostRate(request.getSwapNearLegCostRate());
        payload.setSwapFarLegRate(request.getSwapFarLegRate());
        payload.setSwapFarLegCostRate(request.getSwapFarLegCostRate());
        payload.setNearLegAccount(request.getNearLegAccount());
        payload.setFarLegAccount(request.getFarLegAccount());
        payload.setNettingCurrency(request.getNettingCurrency());
        payload.setNettingAccount(request.getNettingAccount());
        payload.setNettingAmount(request.getNettingAmount());
        payload.setRemark(request.getRemark());

        // 序列化载荷为 JSON
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "序列化任务载荷失败");
        }

        // 查询机构信息
        Integer orgLevel = null;
        if (original.getBranchCode() != null) {
            FxOrg org = fxOrgMapper.selectByOrgCode(original.getBranchCode());
            if (org != null) {
                orgLevel = org.getOrgLevel();
            }
        }

        // 创建提前违约任务
        taskService.createTask(
                TaskType.EARLY_DEFAULT.name(),
                original.getTradeId(),
                original.getBusinessNo(),
                "提前违约",
                RoleCode.CHECKER.name(),
                original.getBranchCode(),
                orgLevel,
                payloadJson
        );

        return original.getTradeId();
    }

    /**
     * 释放提前违约任务冻结的轧差金额
     * 当提前违约任务被拒绝或退回时调用，将冻结的轧差金额从轧差账户中解冻
     */
    private void releaseNettingFreeze(FxTask task, FxTradeMaster master) {
        if (task == null || task.getPayload() == null) {
            return;
        }
        if (!TaskType.EARLY_DEFAULT.name().equals(task.getTaskType())) {
            return;
        }
        try {
            EarlyDefaultTaskPayload payload = objectMapper.readValue(task.getPayload(), EarlyDefaultTaskPayload.class);
            if (payload.getNettingAccount() == null || payload.getNettingAmount() == null
                    || payload.getNettingAmount().compareTo(BigDecimal.ZERO) <= 0) {
                return;
            }
            // 查找轧差账户并解冻
            List<FxCustomerAccount> accounts = fxCustomerAccountMapper.selectByCustomerIdAndCurrency(
                    master.getCustomerId(), payload.getNettingCurrency());
            if (accounts != null) {
                for (FxCustomerAccount acc : accounts) {
                    if (payload.getNettingAccount().equals(acc.getAccountNo())) {
                        BigDecimal frozen = acc.getFrozenAmount() != null ? acc.getFrozenAmount() : BigDecimal.ZERO;
                        BigDecimal newFrozen = frozen.subtract(payload.getNettingAmount());
                        if (newFrozen.compareTo(BigDecimal.ZERO) < 0) {
                            newFrozen = BigDecimal.ZERO;
                        }
                        acc.setFrozenAmount(newFrozen);
                        fxCustomerAccountMapper.update(acc);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "释放轧差冻结金额失败：" + e.getMessage());
        }
    }

    /**
     * 原价展期：
     * 展期交易体现为一笔掉期交易：近端与原交易方向相反（平掉旧交易），远端与原交易方向相同（新远期交易）
     * 原价展期近端/远端成本汇率、客户汇率均取原交易汇率，不产生轧差
     * 1. 原交易状态 ACTIVE -> ROLLED_OVER，原交易交割方式改为无需交割（被掉期近端平掉）
     * 2. 生成一笔掉期交易（近端无需交割，远端全额交割），状态为待复核
     * 3. 为新掉期交易创建生命周期复核任务
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String rolloverOriginal(String tradeId, RolloverRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        FxTradeMaster original = getTradeOrThrow(tradeId);
        validateActiveStatus(original);
        validateForwardOrSwap(original);

        String beforeStatus = original.getStatus();
        String afterStatus = TradeStatus.ROLLED_OVER.name();

        original.setStatus(afterStatus);
        fxTradeMasterMapper.update(original);

        // 原交易被掉期近端平掉，交割方式改为无需交割
        fxTradeMasterMapper.updateSettlementMethod(original.getTradeId(), SettlementMethod.NONE.name());
        if (TradeType.FORWARD.name().equals(original.getTradeType())) {
            var forwardTrade = fxForwardTradeMapper.selectByTradeId(original.getTradeId());
            if (forwardTrade != null) {
                forwardTrade.setSettlementMethod(SettlementMethod.NONE.name());
                fxForwardTradeMapper.update(forwardTrade);
            }
        }

        tradeLifecycleService.recordEvent(tradeId, "ROLLOVER_ORIGINAL", currentUserId,
                beforeStatus, afterStatus, original.getNotionalAmount(), original.getCustomerRate(),
                null, "原价展期：" + request.getRemark());

        // 生成掉期交易：原价展期，近端无需交割，远端全额交割
        String newTradeId = generateSwapForRollover(original, request, currentUserId,
                SpecialTradeType.ROLLOVER_ORIGINAL);

        createLifecycleCheckTask(newTradeId, original.getBusinessNo(), TradeType.SWAP.name(),
                original.getBranchCode(), currentUserId);

        return newTradeId;
    }

    /**
     * 市价展期：
     * 展期交易体现为一笔掉期交易：近端与原交易方向相反（平掉旧交易），远端与原交易方向相同（新远期交易）
     * 市价展期近端/远端成本汇率、客户汇率均取市场实时汇率，产生轧差
     * 1. 原交易状态 ACTIVE -> CLOSED，原交易交割方式改为无需交割（被掉期近端平掉）
     * 2. 生成一笔掉期交易（近端差额交割，远端全额交割，含轧差信息），状态为待复核
     * 3. 为新掉期交易创建生命周期复核任务
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String rolloverMarket(String tradeId, RolloverMarketRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        FxTradeMaster original = getTradeOrThrow(tradeId);
        validateActiveStatus(original);
        validateForwardOrSwap(original);

        String beforeStatus = original.getStatus();
        String afterStatus = TradeStatus.CLOSED.name();

        original.setStatus(afterStatus);
        fxTradeMasterMapper.update(original);

        // 原交易被掉期近端平掉，交割方式改为无需交割
        fxTradeMasterMapper.updateSettlementMethod(original.getTradeId(), SettlementMethod.NONE.name());
        if (TradeType.FORWARD.name().equals(original.getTradeType())) {
            var forwardTrade = fxForwardTradeMapper.selectByTradeId(original.getTradeId());
            if (forwardTrade != null) {
                forwardTrade.setSettlementMethod(SettlementMethod.NONE.name());
                fxForwardTradeMapper.update(forwardTrade);
            }
        }

        tradeLifecycleService.recordEvent(tradeId, "ROLLOVER_MARKET", currentUserId,
                beforeStatus, afterStatus, original.getNotionalAmount(), original.getCustomerRate(),
                null, "市价展期：客户损益=" + request.getCustomerPnl() + "，" + request.getRemark());

        // 生成掉期交易：市价展期，近端差额交割，远端全额交割
        String newTradeId = generateSwapForRolloverMarket(original, request, currentUserId);

        createLifecycleCheckTask(newTradeId, original.getBusinessNo(), TradeType.SWAP.name(),
                original.getBranchCode(), currentUserId);

        return newTradeId;
    }

    /**
     * 保证金增补：增加保证金账户余额，记录生命周期事件（状态不变）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void marginSupplement(String tradeId, MarginSupplementRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        FxTradeMaster original = getTradeOrThrow(tradeId);

        // 增补保证金
        marginService.supplement(request.getMarginAccountId(), request.getSupplementAmount(),
                tradeId, currentUserId, "保证金增补：" + request.getRemark());

        // 记录生命周期事件（状态不变）
        tradeLifecycleService.recordEvent(tradeId, "MARGIN_SUPPLEMENT", currentUserId,
                original.getStatus(), original.getStatus(), request.getSupplementAmount(), null,
                null, "保证金增补：" + request.getRemark());
    }

    /**
     * 全部违约（掉期近端未到期）：
     * 1. 原交易状态 ACTIVE -> EARLY_DEFAULTED，记录生命周期事件
     * 2. 生成一笔新掉期交易与原交易抵消（方向相反、汇率采用实时价格）
     * 3. 若有违约金则从保证金账户扣减
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void fullDefault(String tradeId, FullDefaultRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        FxTradeMaster original = getTradeOrThrow(tradeId);
        validateActiveStatus(original);

        String beforeStatus = original.getStatus();
        String afterStatus = TradeStatus.EARLY_DEFAULTED.name();

        original.setStatus(afterStatus);
        fxTradeMasterMapper.update(original);

        tradeLifecycleService.recordEvent(tradeId, "FULL_DEFAULT", currentUserId,
                beforeStatus, afterStatus, request.getPenaltyAmount(), original.getCustomerRate(),
                null, "全部违约：" + request.getRemark());

        // 生成抵消掉期交易（方向与原交易相反，汇率采用实时价格）
        String newTradeId = generateTradeId(Constants.PREFIX_SWAP);
        String businessNo = generateBusinessNo(Constants.PREFIX_SWAP);

        FxTradeMaster master = copyBaseInfo(original, newTradeId, businessNo);
        master.setTradeType(TradeType.SWAP.name());
        master.setStatus(TradeStatus.PENDING_CHECK.name());
        master.setSpecialTradeType(SpecialTradeType.FULL_DEFAULT.name());
        master.setOriginalTradeType(original.getTradeType());
        master.setOriginalTradeId(original.getTradeId());
        master.setNotionalAmount(request.getNearLegAmount());
        master.setTradeDirection(request.getNearLegDirection());
        master.setValueDate(request.getNearLegValueDate());
        master.setTradeDate(request.getTradeDate());
        master.setMaturityDate(request.getFarLegValueDate());
        master.setCustomerRate(request.getCustomerRate());
        master.setCostRate(request.getCostRate());
        master.setBranchProfitPoint(request.getNearLegBranchProfitPoint());
        master.setMakerId(currentUserId);
        master.setMakeTime(LocalDateTime.now());
        master.setVersion(Constants.DEFAULT_VERSION);
        fxTradeMasterMapper.insert(master);

        // 构建抵消掉期子表
        FxSwapTrade swapTrade = new FxSwapTrade();
        swapTrade.setTradeId(newTradeId);
        swapTrade.setSwapType(request.getSwapType());
        swapTrade.setNearLegDirection(request.getNearLegDirection());
        swapTrade.setNearLegAmount(request.getNearLegAmount());
        swapTrade.setNearLegRate(request.getNearLegRate());
        swapTrade.setNearLegCostRate(request.getNearLegCostRate());
        swapTrade.setNearLegCustomerRate(request.getNearLegCustomerRate());
        swapTrade.setNearLegBranchProfitPoint(request.getNearLegBranchProfitPoint());
        swapTrade.setNearLegValueDate(request.getNearLegValueDate());
        swapTrade.setNearLegAccount(request.getNearLegAccount());
        swapTrade.setNearLegCurrency1Account(request.getNearLegCurrency1Account());
        swapTrade.setNearLegCurrency2Account(request.getNearLegCurrency2Account());
        swapTrade.setNearLegSettlementMethod(request.getNearLegSettlementMethod());
        swapTrade.setFarLegDirection(request.getFarLegDirection());
        swapTrade.setFarLegAmount(request.getFarLegAmount());
        swapTrade.setFarLegRate(request.getFarLegRate());
        swapTrade.setFarLegCostRate(request.getFarLegCostRate());
        swapTrade.setFarLegCustomerRate(request.getFarLegCustomerRate());
        swapTrade.setFarLegBranchProfitPoint(request.getFarLegBranchProfitPoint());
        swapTrade.setFarLegValueDate(request.getFarLegValueDate());
        swapTrade.setFarLegAccount(request.getFarLegAccount());
        swapTrade.setFarLegCurrency1Account(request.getFarLegCurrency1Account());
        swapTrade.setFarLegCurrency2Account(request.getFarLegCurrency2Account());
        swapTrade.setFarLegSettlementMethod(request.getFarLegSettlementMethod());
        swapTrade.setTerm(request.getTerm());
        swapTrade.setSwapPoint(request.getSwapPoint());
        swapTrade.setNearSpotRate(request.getNearSpotRate());
        swapTrade.setIsPureSwap(Constants.NO);
        fxSwapTradeMapper.insert(swapTrade);

        // 记录抵消交易的生命周期事件
        tradeLifecycleService.recordEvent(newTradeId, Constants.NODE_MAKE, currentUserId,
                null, TradeStatus.PENDING_CHECK.name(), request.getNearLegAmount(), request.getNearLegRate(),
                null, "全部违约-生成抵消掉期交易");

        // 处理违约金扣减
        if (request.getPenaltyAmount() != null && request.getPenaltyAmount().compareTo(BigDecimal.ZERO) > 0) {
            marginService.deduct(request.getPenaltyAccount(), request.getPenaltyAmount(),
                    tradeId, currentUserId, "全部违约违约金");
        }
    }

    // ==================== 私有辅助方法 ====================

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
     * 校验任务存在、状态合法、与交易ID匹配
     */
    private FxTask validateTask(Long taskId, String tradeId) {
        FxTask task = taskService.getByTaskId(taskId);
        if (task == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "任务不存在");
        }
        String status = task.getStatus();
        if (!TaskStatus.PENDING.name().equals(status)) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "任务当前状态不允许操作");
        }
        if (task.getTradeId() != null && !task.getTradeId().equals(tradeId)) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "任务与交易不匹配");
        }
        return task;
    }

    /**
     * 判断是否需要授权：交易金额超过机构审批额度时需要授权
     */
    private boolean needAuthorization(FxTradeMaster master) {
        if (master.getBranchCode() == null) {
            return false;
        }
        FxOrg org = fxOrgMapper.selectByOrgCode(master.getBranchCode());
        if (org == null || org.getApprovalLimit() == null) {
            return false;
        }
        BigDecimal amount = master.getNotionalAmount() == null ? BigDecimal.ZERO : master.getNotionalAmount();
        return amount.compareTo(org.getApprovalLimit()) > 0;
    }

    /**
     * 创建授权任务，指派给上级机构的授权角色
     */
    private void createAuthorizeTask(FxTradeMaster master) {
        String parentOrgCode = null;
        Integer parentOrgLevel = null;
        if (master.getBranchCode() != null) {
            FxOrg org = fxOrgMapper.selectByOrgCode(master.getBranchCode());
            if (org != null) {
                parentOrgCode = org.getParentOrgCode();
                if (parentOrgCode != null) {
                    FxOrg parentOrg = fxOrgMapper.selectByOrgCode(parentOrgCode);
                    if (parentOrg != null) {
                        parentOrgLevel = parentOrg.getOrgLevel();
                    }
                }
            }
        }
        taskService.createTask(TaskType.AUTHORIZE.name(), master.getTradeId(), master.getBusinessNo(),
                master.getTradeType(), RoleCode.AUTHORIZER.name(), parentOrgCode, parentOrgLevel, null);
    }

    /**
     * 校验交易状态为生效
     */
    private void validateActiveStatus(FxTradeMaster master) {
        if (!TradeStatus.ACTIVE.name().equals(master.getStatus())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "交易当前状态不允许此操作，需为生效状态");
        }
    }

    /**
     * 校验交易类型为远期或掉期
     */
    private void validateForwardOrSwap(FxTradeMaster master) {
        String tradeType = master.getTradeType();
        if (!TradeType.FORWARD.name().equals(tradeType) && !TradeType.SWAP.name().equals(tradeType)) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "仅远期或掉期交易支持此操作");
        }
    }

    /**
     * 创建生命周期复核任务，指派给当前机构的复核角色
     */
    private void createLifecycleCheckTask(String tradeId, String businessNo, String tradeType,
                                          String branchCode, Long currentUserId) {
        Integer orgLevel = null;
        if (branchCode != null) {
            FxOrg org = fxOrgMapper.selectByOrgCode(branchCode);
            if (org != null) {
                orgLevel = org.getOrgLevel();
            }
        }
        taskService.createTask(TaskType.CHECK_LIFECYCLE.name(), tradeId, businessNo, tradeType,
                RoleCode.CHECKER.name(), branchCode, orgLevel, null);
    }

    /**
     * 生成提前交割的掉期交易（从任务载荷生成）：
     * 近端：与原交易方向相同，使用新客户汇率（包含或不包含惩罚）
     * 远端：与原交易方向相反，使用原交易客户汇率（用于抵消原交易）
     */
    private String generateSwapForEarlyDelivery(FxTradeMaster original, EarlyDeliveryTaskPayload payload, Long currentUserId) {
        String newTradeId = generateTradeId(Constants.PREFIX_SWAP);
        String businessNo = generateBusinessNo(Constants.PREFIX_SWAP);

        // 近端交易方向：与原交易方向相同
        String nearLegDirection = original.getTradeDirection();
        // 远端交易方向：与原交易方向相反
        String farLegDirection = reverseDirection(original.getTradeDirection());
        
        // 近端客户汇率：取用户输入的近端汇率
        BigDecimal nearLegCustomerRate = payload.getNearLegCustomerRate() != null ? payload.getNearLegCustomerRate() : original.getCustomerRate();
        // 近端成本汇率
        BigDecimal nearLegCostRate = payload.getNearLegCostRate() != null ? payload.getNearLegCostRate() : original.getCostRate();
        
        // 远端客户汇率：取原交易客户汇率
        BigDecimal farLegCustomerRate = payload.getFarLegCustomerRate() != null ? payload.getFarLegCustomerRate() : original.getCustomerRate();
        // 远端成本汇率
        BigDecimal farLegCostRate = payload.getFarLegCostRate() != null ? payload.getFarLegCostRate() : original.getCostRate();
        
        // 计算近端和远端收益点
        BigDecimal nearLegProfitPoint = calculateBranchProfitPoint(nearLegDirection, nearLegCustomerRate, nearLegCostRate);
        BigDecimal farLegProfitPoint = calculateBranchProfitPoint(farLegDirection, farLegCustomerRate, farLegCostRate);

        FxTradeMaster master = copyBaseInfo(original, newTradeId, businessNo);
        master.setTradeType(TradeType.SWAP.name());
        master.setStatus(TradeStatus.PENDING_CHECK.name());
        master.setSpecialTradeType(SpecialTradeType.EARLY_DELIVERY.name());
        master.setOriginalTradeType(original.getTradeType());
        master.setOriginalTradeId(original.getTradeId());
        master.setNotionalAmount(payload.getOriginalAmount());
        master.setTradeDirection(original.getTradeDirection()); // 掉期主表方向用原交易方向
        master.setTradeDate(LocalDate.now());
        master.setCustomerRate(nearLegCustomerRate); // 主表汇率用近端汇率
        master.setCostRate(nearLegCostRate);
        master.setBranchProfitPoint(nearLegProfitPoint);
        master.setMakerId(currentUserId);
        master.setMakeTime(LocalDateTime.now());
        master.setVersion(Constants.DEFAULT_VERSION);
        fxTradeMasterMapper.insert(master);

        // 构建掉期子表
        FxSwapTrade swapTrade = new FxSwapTrade();
        swapTrade.setTradeId(newTradeId);
        swapTrade.setSwapType(original.getTradeDirection());
        
        // 近端信息：与原交易方向相同，使用新客户汇率
        swapTrade.setNearLegDirection(nearLegDirection);
        swapTrade.setNearLegAmount(payload.getOriginalAmount());
        swapTrade.setNearLegRate(nearLegCustomerRate);
        swapTrade.setNearLegCostRate(nearLegCostRate);
        swapTrade.setNearLegBranchProfitPoint(nearLegProfitPoint);
        swapTrade.setNearLegValueDate(payload.getNearLegValueDate() != null ? payload.getNearLegValueDate() : LocalDate.now());
        swapTrade.setNearLegAccount(payload.getNearLegAccount1()); // 账户1作为主账户
        
        // 远端信息：与原交易方向相反，使用原交易客户汇率
        swapTrade.setFarLegDirection(farLegDirection);
        swapTrade.setFarLegAmount(payload.getOriginalAmount());
        swapTrade.setFarLegRate(farLegCustomerRate);
        swapTrade.setFarLegCostRate(farLegCostRate);
        swapTrade.setFarLegBranchProfitPoint(farLegProfitPoint);
        swapTrade.setFarLegValueDate(payload.getFarLegValueDate() != null ? payload.getFarLegValueDate() : original.getMaturityDate());
        swapTrade.setFarLegAccount(payload.getNearLegAccount2());
        
        swapTrade.setIsPureSwap(Constants.NO);
        fxSwapTradeMapper.insert(swapTrade);

        return newTradeId;
    }

    /**
     * 生成提前违约的即期交易（反向平盘，与掉期近端轧差产生损益）
     * 方向：与原交易相反
     * 汇率：使用即期客户汇率
     */
    private String generateSpotForEarlyDefault(FxTradeMaster original, EarlyDefaultRequest request, Long currentUserId) {
        String newTradeId = generateTradeId(Constants.PREFIX_SPOT);
        String businessNo = generateBusinessNo(Constants.PREFIX_SPOT);

        FxTradeMaster master = copyBaseInfo(original, newTradeId, businessNo);
        master.setTradeType(TradeType.SPOT.name());
        master.setStatus(TradeStatus.PENDING_CHECK.name());
        master.setSpecialTradeType(SpecialTradeType.EARLY_DEFAULT.name());
        master.setOriginalTradeId(original.getTradeId());
        master.setNotionalAmount(request.getDefaultAmount());
        // 方向：与原交易相反，用于与掉期近端轧差
        master.setTradeDirection(reverseDirection(original.getTradeDirection()));
        master.setValueDate(request.getSwapNearLegValueDate() != null ? request.getSwapNearLegValueDate() : LocalDate.now());
        master.setTradeDate(LocalDate.now());
        // 汇率：优先使用即期客户汇率
        BigDecimal spotRate = request.getSpotCustomerRate() != null ? request.getSpotCustomerRate() : 
                             (request.getSpotMarketRate() != null ? request.getSpotMarketRate() : original.getSpotRate());
        master.setSpotRate(spotRate);
        master.setCustomerRate(spotRate);
        master.setCostRate(spotRate);
        master.setSettlementMethod(SettlementMethod.NET.name());
        master.setMaturityDate(LocalDate.now());
        master.setMakerId(currentUserId);
        master.setMakeTime(LocalDateTime.now());
        master.setVersion(Constants.DEFAULT_VERSION);
        fxTradeMasterMapper.insert(master);

        // 构建即期子表
        FxSpotTrade spotTrade = new FxSpotTrade();
        spotTrade.setTradeId(newTradeId);
        spotTrade.setSettlementType(original.getDeliveryType());
        spotTrade.setSpotRate(spotRate);
        spotTrade.setCustomerRate(spotRate);
        spotTrade.setCostRate(spotRate);
        spotTrade.setAmount(request.getDefaultAmount());
        fxSpotTradeMapper.insert(spotTrade);

        return newTradeId;
    }

    /**
     * 生成提前违约的掉期交易
     * 近端：与原交易方向相同，使用含惩罚的汇率
     * 远端：与原交易方向相反，使用原交易汇率（用于抵消原交易）
     */
    private String generateSwapForEarlyDefault(FxTradeMaster original, EarlyDefaultRequest request, Long currentUserId) {
        String newTradeId = generateTradeId(Constants.PREFIX_SWAP);
        String businessNo = generateBusinessNo(Constants.PREFIX_SWAP);

        FxTradeMaster master = copyBaseInfo(original, newTradeId, businessNo);
        master.setTradeType(TradeType.SWAP.name());
        master.setStatus(TradeStatus.PENDING_CHECK.name());
        master.setSpecialTradeType(SpecialTradeType.EARLY_DEFAULT.name());
        master.setOriginalTradeId(original.getTradeId());
        master.setNotionalAmount(request.getDefaultAmount());
        master.setSettlementMethod(SettlementMethod.NONE.name());
        master.setMakerId(currentUserId);
        master.setMakeTime(LocalDateTime.now());
        master.setVersion(Constants.DEFAULT_VERSION);
        fxTradeMasterMapper.insert(master);

        // 构建掉期子表
        FxSwapTrade swapTrade = new FxSwapTrade();
        swapTrade.setTradeId(newTradeId);
        swapTrade.setSwapType(original.getTradeDirection());
        // 近端：与原交易方向相同，使用含惩罚的汇率（优先使用penaltyRate）
        swapTrade.setNearLegDirection(original.getTradeDirection());
        swapTrade.setNearLegAmount(request.getDefaultAmount());
        BigDecimal nearLegRate = request.getPenaltyRate() != null ? request.getPenaltyRate() :
                                (request.getSwapNearLegRate() != null ? request.getSwapNearLegRate() : original.getCustomerRate());
        swapTrade.setNearLegRate(nearLegRate);
        swapTrade.setNearLegValueDate(request.getSwapNearLegValueDate() != null ? request.getSwapNearLegValueDate() : LocalDate.now());
        swapTrade.setNearLegAccount(request.getNearLegAccount());
        swapTrade.setNearLegSettlementMethod(SettlementMethod.NONE.name());
        // 远端：与原交易方向相反，使用原交易汇率（用于抵消原交易）
        swapTrade.setFarLegDirection(reverseDirection(original.getTradeDirection()));
        swapTrade.setFarLegAmount(request.getDefaultAmount());
        BigDecimal farLegRate = request.getSwapFarLegRate() != null ? request.getSwapFarLegRate() : original.getCustomerRate();
        swapTrade.setFarLegRate(farLegRate); // 远端保持原交易汇率
        swapTrade.setFarLegValueDate(original.getMaturityDate());
        swapTrade.setFarLegAccount(request.getFarLegAccount());
        swapTrade.setFarLegSettlementMethod(SettlementMethod.NONE.name());
        swapTrade.setIsPureSwap(Constants.NO);
        fxSwapTradeMapper.insert(swapTrade);

        return newTradeId;
    }

    /**
     * 计算分行收益点
     * 交易方向为客户买入：(客户汇率 - 成本汇率) * 1000
     * 交易方向为客户卖出：(成本汇率 - 客户汇率) * 1000
     */
    private BigDecimal calculateBranchProfitPoint(String tradeDirection, BigDecimal customerRate, BigDecimal costRate) {
        if (customerRate == null || costRate == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal profit;
        if ("BUY".equals(tradeDirection)) {
            profit = customerRate.subtract(costRate);
        } else {
            profit = costRate.subtract(customerRate);
        }
        return profit.multiply(new BigDecimal("1000"));
    }

    /**
     * 重载方法：从任务载荷生成提前违约的即期交易
     */
    private String generateSpotForEarlyDefault(FxTradeMaster original, EarlyDefaultTaskPayload payload, Long currentUserId) {
        String newTradeId = generateTradeId(Constants.PREFIX_SPOT);
        String businessNo = generateBusinessNo(Constants.PREFIX_SPOT);

        // 即期交易方向：与原交易相反（因为是提前违约的反向平盘）
        String spotDirection = reverseDirection(original.getTradeDirection());
        
        // 客户汇率：使用即期客户汇率
        BigDecimal spotCustomerRate = payload.getSpotCustomerRate() != null ? payload.getSpotCustomerRate() : original.getCustomerRate();
        
        // 成本汇率：根据方向计算
        // 交易方向为BUY（客户买入），取总/分卖价
        // 交易方向为SELL（客户卖出），取总/分买价
        BigDecimal spotCostRate = payload.getSpotCostRate() != null ? payload.getSpotCostRate() : original.getCostRate();
        
        // 计算分行收益点
        BigDecimal branchProfitPoint = calculateBranchProfitPoint(spotDirection, spotCustomerRate, spotCostRate);

        FxTradeMaster master = copyBaseInfo(original, newTradeId, businessNo);
        master.setTradeType(TradeType.SPOT.name());
        master.setStatus(TradeStatus.ACTIVE.name());
        master.setSpecialTradeType(SpecialTradeType.EARLY_DEFAULT.name());
        master.setOriginalTradeType(original.getTradeType());
        master.setOriginalTradeId(original.getTradeId());
        master.setNotionalAmount(payload.getDefaultAmount());
        master.setTradeDirection(spotDirection);
        master.setValueDate(payload.getSwapNearLegValueDate() != null ? payload.getSwapNearLegValueDate() : LocalDate.now());
        master.setTradeDate(LocalDate.now());
        master.setSpotRate(spotCustomerRate);
        master.setCustomerRate(spotCustomerRate);
        master.setCostRate(spotCostRate);
        master.setBranchProfitPoint(branchProfitPoint);
        master.setSettlementMethod(SettlementMethod.NET.name());
        master.setMaturityDate(LocalDate.now());
        master.setNettingCurrency(payload.getNettingCurrency());
        master.setNettingAccount(payload.getNettingAccount());
        master.setNettingAmount(payload.getNettingAmount());
        master.setMakerId(currentUserId);
        master.setMakeTime(LocalDateTime.now());
        master.setCheckerId(currentUserId);
        master.setCheckTime(LocalDateTime.now());
        master.setVersion(Constants.DEFAULT_VERSION);
        fxTradeMasterMapper.insert(master);

        // 构建即期子表
        FxSpotTrade spotTrade = new FxSpotTrade();
        spotTrade.setTradeId(newTradeId);
        spotTrade.setSettlementType(original.getDeliveryType());
        spotTrade.setSpotRate(spotCustomerRate);
        spotTrade.setCustomerRate(spotCustomerRate);
        spotTrade.setCostRate(spotCostRate);
        spotTrade.setAmount(payload.getDefaultAmount());
        fxSpotTradeMapper.insert(spotTrade);

        return newTradeId;
    }

    /**
     * 重载方法：从任务载荷生成提前违约的掉期交易
     */
    private String generateSwapForEarlyDefault(FxTradeMaster original, EarlyDefaultTaskPayload payload, Long currentUserId) {
        String newTradeId = generateTradeId(Constants.PREFIX_SWAP);
        String businessNo = generateBusinessNo(Constants.PREFIX_SWAP);

        // 近端交易方向：与原交易方向相同
        String nearLegDirection = original.getTradeDirection();
        // 远端交易方向：与原交易方向相反
        String farLegDirection = reverseDirection(original.getTradeDirection());
        
        // 近端客户汇率：取惩罚汇率
        BigDecimal nearLegCustomerRate = payload.getPenaltyRate() != null ? payload.getPenaltyRate() : 
                                      (payload.getSwapNearLegRate() != null ? payload.getSwapNearLegRate() : original.getCustomerRate());
        // 近端成本汇率
        BigDecimal nearLegCostRate = payload.getSwapNearLegCostRate() != null ? payload.getSwapNearLegCostRate() : original.getCostRate();
        
        // 远端客户汇率：取原交易客户汇率
        BigDecimal farLegCustomerRate = payload.getSwapFarLegRate() != null ? payload.getSwapFarLegRate() : original.getCustomerRate();
        // 远端成本汇率
        BigDecimal farLegCostRate = payload.getSwapFarLegCostRate() != null ? payload.getSwapFarLegCostRate() : original.getCostRate();
        
        // 计算近端和远端收益点
        BigDecimal nearLegProfitPoint = calculateBranchProfitPoint(nearLegDirection, nearLegCustomerRate, nearLegCostRate);
        BigDecimal farLegProfitPoint = calculateBranchProfitPoint(farLegDirection, farLegCustomerRate, farLegCostRate);

        FxTradeMaster master = copyBaseInfo(original, newTradeId, businessNo);
        master.setTradeType(TradeType.SWAP.name());
        master.setStatus(TradeStatus.ACTIVE.name());
        master.setSpecialTradeType(SpecialTradeType.EARLY_DEFAULT.name());
        master.setOriginalTradeType(original.getTradeType());
        master.setOriginalTradeId(original.getTradeId());
        master.setNotionalAmount(payload.getDefaultAmount());
        master.setTradeDirection(original.getTradeDirection()); // 掉期主表方向用原交易方向
        master.setTradeDate(LocalDate.now());
        master.setCustomerRate(nearLegCustomerRate); // 主表汇率用近端汇率
        master.setCostRate(nearLegCostRate);
        master.setBranchProfitPoint(nearLegProfitPoint);
        master.setNettingCurrency(payload.getNettingCurrency());
        master.setNettingAccount(payload.getNettingAccount());
        master.setNettingAmount(payload.getNettingAmount());
        master.setSettlementMethod(SettlementMethod.NONE.name());
        master.setMakerId(currentUserId);
        master.setMakeTime(LocalDateTime.now());
        master.setCheckerId(currentUserId);
        master.setCheckTime(LocalDateTime.now());
        master.setVersion(Constants.DEFAULT_VERSION);
        fxTradeMasterMapper.insert(master);

        // 构建掉期子表
        FxSwapTrade swapTrade = new FxSwapTrade();
        swapTrade.setTradeId(newTradeId);
        swapTrade.setSwapType(original.getTradeDirection());

        // 近端信息
        swapTrade.setNearLegDirection(nearLegDirection);
        swapTrade.setNearLegAmount(payload.getDefaultAmount());
        swapTrade.setNearLegRate(nearLegCustomerRate);
        swapTrade.setNearLegCostRate(nearLegCostRate);
        swapTrade.setNearLegBranchProfitPoint(nearLegProfitPoint);
        swapTrade.setNearLegValueDate(payload.getSwapNearLegValueDate() != null ? payload.getSwapNearLegValueDate() : LocalDate.now());
        swapTrade.setNearLegAccount(payload.getNearLegAccount());
        swapTrade.setNearLegSettlementMethod(SettlementMethod.NONE.name());

        // 远端信息
        swapTrade.setFarLegDirection(farLegDirection);
        swapTrade.setFarLegAmount(payload.getDefaultAmount());
        swapTrade.setFarLegRate(farLegCustomerRate);
        swapTrade.setFarLegCostRate(farLegCostRate);
        swapTrade.setFarLegBranchProfitPoint(farLegProfitPoint);
        swapTrade.setFarLegValueDate(original.getMaturityDate());
        swapTrade.setFarLegAccount(payload.getFarLegAccount());
        swapTrade.setFarLegSettlementMethod(SettlementMethod.NONE.name());

        swapTrade.setIsPureSwap(Constants.NO);
        fxSwapTradeMapper.insert(swapTrade);

        return newTradeId;
    }

    /**
     * 生成原价展期的掉期交易：
     * 近端：与原交易方向相反（平掉旧交易），使用原交易汇率，交割方式=无需交割
     * 远端：与原交易方向相同（新远期交易），使用原交易汇率，交割方式=全额交割
     */
    private String generateSwapForRollover(FxTradeMaster original, RolloverRequest request, Long currentUserId,
                                           SpecialTradeType specialType) {
        String newTradeId = generateTradeId(Constants.PREFIX_SWAP);
        String businessNo = generateBusinessNo(Constants.PREFIX_SWAP);

        // 近端方向：与原交易方向相反（平掉旧交易）
        String nearLegDirection = reverseDirection(original.getTradeDirection());
        // 远端方向：与原交易方向相同（新远期交易）
        String farLegDirection = original.getTradeDirection();

        // 原价展期：近端/远端汇率取原交易汇率
        BigDecimal nearLegCostRate = request.getNearLegCostRate() != null ? request.getNearLegCostRate() : original.getCostRate();
        BigDecimal nearLegCustomerRate = request.getNearLegCustomerRate() != null ? request.getNearLegCustomerRate() : original.getCustomerRate();
        BigDecimal farLegCostRate = request.getFarLegCostRate() != null ? request.getFarLegCostRate() : original.getCostRate();
        BigDecimal farLegCustomerRate = request.getFarLegCustomerRate() != null ? request.getFarLegCustomerRate() : original.getCustomerRate();

        BigDecimal nearLegProfitPoint = calculateBranchProfitPoint(nearLegDirection, nearLegCustomerRate, nearLegCostRate);
        BigDecimal farLegProfitPoint = calculateBranchProfitPoint(farLegDirection, farLegCustomerRate, farLegCostRate);

        // 原价展期：近端无需交割，远端全额交割
        String nearLegSettlement = SettlementMethod.NONE.name();
        String farLegSettlement = SettlementMethod.FULL.name();

        FxTradeMaster master = copyBaseInfo(original, newTradeId, businessNo);
        master.setTradeType(TradeType.SWAP.name());
        master.setStatus(TradeStatus.PENDING_CHECK.name());
        master.setSpecialTradeType(specialType.name());
        master.setOriginalTradeType(original.getTradeType());
        master.setOriginalTradeId(original.getTradeId());
        master.setNotionalAmount(original.getNotionalAmount());
        master.setTradeDirection(original.getTradeDirection());
        master.setTradeDate(LocalDate.now());
        master.setValueDate(original.getMaturityDate()); // 近端起息日 = 原到期日
        master.setMaturityDate(request.getNewMaturityDate()); // 远端到期日
        master.setCustomerRate(farLegCustomerRate);
        master.setCostRate(farLegCostRate);
        master.setBranchProfitPoint(farLegProfitPoint);
        master.setSettlementMethod(nearLegSettlement);
        master.setMakerId(currentUserId);
        master.setMakeTime(LocalDateTime.now());
        master.setVersion(Constants.DEFAULT_VERSION);
        fxTradeMasterMapper.insert(master);

        // 构建掉期子表
        FxSwapTrade swapTrade = new FxSwapTrade();
        swapTrade.setTradeId(newTradeId);
        // 掉期类型：原BUY→近卖远买=S_B，原SELL→近买远卖=B_S
        swapTrade.setSwapType("BUY".equals(original.getTradeDirection()) ? "S_B" : "B_S");

        // 近端：与原交易方向相反，使用原交易汇率，无需交割
        swapTrade.setNearLegDirection(nearLegDirection);
        swapTrade.setNearLegAmount(original.getNotionalAmount());
        swapTrade.setNearLegRate(nearLegCustomerRate);
        swapTrade.setNearLegCostRate(nearLegCostRate);
        swapTrade.setNearLegCustomerRate(nearLegCustomerRate);
        swapTrade.setNearLegBranchProfitPoint(nearLegProfitPoint);
        swapTrade.setNearLegValueDate(original.getMaturityDate());
        swapTrade.setNearLegSettlementMethod(nearLegSettlement);

        // 远端：与原交易方向相同，使用原交易汇率，全额交割
        swapTrade.setFarLegDirection(farLegDirection);
        swapTrade.setFarLegAmount(request.getFarLegAmount());
        swapTrade.setFarLegRate(farLegCustomerRate);
        swapTrade.setFarLegCostRate(farLegCostRate);
        swapTrade.setFarLegCustomerRate(farLegCustomerRate);
        swapTrade.setFarLegBranchProfitPoint(farLegProfitPoint);
        swapTrade.setFarLegValueDate(request.getNewMaturityDate());
        swapTrade.setFarLegCurrency1Account(request.getFarLegCurrency1Account());
        swapTrade.setFarLegCurrency2Account(request.getFarLegCurrency2Account());
        swapTrade.setFarLegSettlementMethod(farLegSettlement);

        swapTrade.setIsPureSwap(Constants.NO);
        fxSwapTradeMapper.insert(swapTrade);

        return newTradeId;
    }

    /**
     * 生成市价展期的掉期交易：
     * 近端：与原交易方向相反（平掉旧交易），使用市场实时汇率，交割方式=差额交割，含轧差信息
     * 远端：与原交易方向相同（新远期交易），使用市场实时汇率，交割方式=全额交割
     */
    private String generateSwapForRolloverMarket(FxTradeMaster original, RolloverMarketRequest request, Long currentUserId) {
        String newTradeId = generateTradeId(Constants.PREFIX_SWAP);
        String businessNo = generateBusinessNo(Constants.PREFIX_SWAP);

        // 近端方向：与原交易方向相反（平掉旧交易）
        String nearLegDirection = reverseDirection(original.getTradeDirection());
        // 远端方向：与原交易方向相同（新远期交易）
        String farLegDirection = original.getTradeDirection();

        // 市价展期：近端/远端汇率取市场实时汇率
        BigDecimal nearLegCostRate = request.getNearLegCostRate() != null ? request.getNearLegCostRate() : original.getCostRate();
        BigDecimal nearLegCustomerRate = request.getNearLegCustomerRate() != null ? request.getNearLegCustomerRate() : original.getCustomerRate();
        BigDecimal farLegCostRate = request.getFarLegCostRate() != null ? request.getFarLegCostRate() : original.getCostRate();
        BigDecimal farLegCustomerRate = request.getFarLegCustomerRate() != null ? request.getFarLegCustomerRate() : original.getCustomerRate();

        BigDecimal nearLegProfitPoint = calculateBranchProfitPoint(nearLegDirection, nearLegCustomerRate, nearLegCostRate);
        BigDecimal farLegProfitPoint = calculateBranchProfitPoint(farLegDirection, farLegCustomerRate, farLegCostRate);

        // 市价展期：近端差额交割（产生轧差），远端全额交割
        String nearLegSettlement = SettlementMethod.NET.name();
        String farLegSettlement = SettlementMethod.FULL.name();

        FxTradeMaster master = copyBaseInfo(original, newTradeId, businessNo);
        master.setTradeType(TradeType.SWAP.name());
        master.setStatus(TradeStatus.PENDING_CHECK.name());
        master.setSpecialTradeType(SpecialTradeType.ROLLOVER_MARKET.name());
        master.setOriginalTradeType(original.getTradeType());
        master.setOriginalTradeId(original.getTradeId());
        master.setNotionalAmount(original.getNotionalAmount());
        master.setTradeDirection(original.getTradeDirection());
        master.setTradeDate(LocalDate.now());
        master.setValueDate(original.getMaturityDate()); // 近端起息日 = 原到期日
        master.setMaturityDate(request.getNewMaturityDate()); // 远端到期日
        master.setCustomerRate(farLegCustomerRate);
        master.setCostRate(farLegCostRate);
        master.setBranchProfitPoint(farLegProfitPoint);
        master.setSettlementMethod(nearLegSettlement);
        // 市价展期轧差信息存储在主表
        master.setNettingCurrency(request.getNettingCurrency());
        master.setNettingAccount(request.getNettingAccount());
        master.setNettingAmount(request.getNettingAmount());
        master.setMakerId(currentUserId);
        master.setMakeTime(LocalDateTime.now());
        master.setVersion(Constants.DEFAULT_VERSION);
        fxTradeMasterMapper.insert(master);

        // 构建掉期子表
        FxSwapTrade swapTrade = new FxSwapTrade();
        swapTrade.setTradeId(newTradeId);
        // 掉期类型：原BUY→近卖远买=S_B，原SELL→近买远卖=B_S
        swapTrade.setSwapType("BUY".equals(original.getTradeDirection()) ? "S_B" : "B_S");

        // 近端：与原交易方向相反，使用市场汇率，差额交割
        swapTrade.setNearLegDirection(nearLegDirection);
        swapTrade.setNearLegAmount(original.getNotionalAmount());
        swapTrade.setNearLegRate(nearLegCustomerRate);
        swapTrade.setNearLegCostRate(nearLegCostRate);
        swapTrade.setNearLegCustomerRate(nearLegCustomerRate);
        swapTrade.setNearLegBranchProfitPoint(nearLegProfitPoint);
        swapTrade.setNearLegValueDate(original.getMaturityDate());
        swapTrade.setNearLegSettlementMethod(nearLegSettlement);

        // 远端：与原交易方向相同，使用市场汇率，全额交割
        swapTrade.setFarLegDirection(farLegDirection);
        swapTrade.setFarLegAmount(request.getFarLegAmount());
        swapTrade.setFarLegRate(farLegCustomerRate);
        swapTrade.setFarLegCostRate(farLegCostRate);
        swapTrade.setFarLegCustomerRate(farLegCustomerRate);
        swapTrade.setFarLegBranchProfitPoint(farLegProfitPoint);
        swapTrade.setFarLegValueDate(request.getNewMaturityDate());
        swapTrade.setFarLegCurrency1Account(request.getFarLegCurrency1Account());
        swapTrade.setFarLegCurrency2Account(request.getFarLegCurrency2Account());
        swapTrade.setFarLegSettlementMethod(farLegSettlement);

        swapTrade.setIsPureSwap(Constants.NO);
        fxSwapTradeMapper.insert(swapTrade);

        return newTradeId;
    }

    /**
     * 复制原交易基础信息到新交易主表（不含状态、特殊类型等需单独设置的字段）
     */
    private FxTradeMaster copyBaseInfo(FxTradeMaster original, String newTradeId, String businessNo) {
        FxTradeMaster master = new FxTradeMaster();
        master.setTradeId(newTradeId);
        master.setBusinessNo(businessNo);
        master.setBranchCode(original.getBranchCode());
        master.setCustomerId(original.getCustomerId());
        master.setCustomerName(original.getCustomerName());
        master.setBaseCurrency(original.getBaseCurrency());
        master.setQuoteCurrency(original.getQuoteCurrency());
        master.setCurrencyPair(original.getCurrencyPair());
        master.setCounterAmount(original.getCounterAmount());
        master.setTradeDirection(original.getTradeDirection());
        master.setTradeDate(original.getTradeDate());
        master.setDeliveryType(original.getDeliveryType());
        master.setSettlementMethod(original.getSettlementMethod());
        master.setSpotRate(original.getSpotRate());
        master.setCostRate(original.getCostRate());
        master.setBranchProfitPoint(original.getBranchProfitPoint());
        master.setPurposeCode(original.getPurposeCode());
        master.setFxPurposeCode(original.getFxPurposeCode());
        return master;
    }

    /**
     * 反转交易方向：BUY -> SELL，SELL -> BUY
     */
    private String reverseDirection(String direction) {
        if ("BUY".equals(direction)) {
            return "SELL";
        } else if ("SELL".equals(direction)) {
            return "BUY";
        }
        return direction;
    }

    /**
     * 释放交易关联的保证金：根据交易类型查找保证金账户并释放全部占用金额
     */
    private void releaseMarginForTrade(FxTradeMaster original, Long currentUserId, String remark) {
        // 根据客户与币种查找保证金账户
        FxMarginAccount account = fxMarginAccountMapper.selectByCustomerIdAndCurrency(
                original.getCustomerId(), original.getBaseCurrency());
        if (account != null && account.getOccupiedAmount() != null
                && account.getOccupiedAmount().compareTo(BigDecimal.ZERO) > 0) {
            marginService.release(account.getMarginAccountId(), account.getOccupiedAmount(),
                    original.getTradeId(), currentUserId, remark);
        }
    }

    /**
     * 生成交易ID：前缀 + yyyyMMddHHmmss + 3位随机数
     */
    private String generateTradeId(String prefix) {
        String timePart = LocalDateTime.now().format(TRADE_ID_FMT);
        int random = ThreadLocalRandom.current().nextInt(100, 1000);
        return prefix + timePart + random;
    }

    /**
     * 生成业务编号：前缀 + yyyyMMdd + 4位序列号
     */
    private String generateBusinessNo(String prefix) {
        String datePart = LocalDateTime.now().format(BIZ_NO_FMT);
        long seq = System.currentTimeMillis() % 10000;
        return prefix + datePart + String.format("%04d", seq);
    }
}
