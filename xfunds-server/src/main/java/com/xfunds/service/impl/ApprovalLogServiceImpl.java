package com.xfunds.service.impl;

import com.xfunds.entity.FxApprovalLog;
import com.xfunds.mapper.FxApprovalLogMapper;
import com.xfunds.service.ApprovalLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 审批日志服务实现类
 */
@Service
public class ApprovalLogServiceImpl implements ApprovalLogService {

    @Autowired
    private FxApprovalLogMapper fxApprovalLogMapper;

    /**
     * 记录审批日志
     */
    @Override
    public void recordLog(String tradeId, String node, Long approverId, String approverName,
                          String approverOrg, String decision, String comment,
                          String beforeStatus, String afterStatus) {
        FxApprovalLog log = new FxApprovalLog();
        log.setTradeId(tradeId);
        log.setApprovalNode(node);
        log.setApproverId(approverId);
        log.setApproverName(approverName);
        log.setApproverOrg(approverOrg);
        log.setDecision(decision);
        log.setComment(comment);
        log.setBeforeStatus(beforeStatus);
        log.setAfterStatus(afterStatus);
        fxApprovalLogMapper.insert(log);
    }

    /**
     * 根据交易ID查询审批日志列表
     */
    @Override
    public List<FxApprovalLog> listByTradeId(String tradeId) {
        return fxApprovalLogMapper.selectByTradeId(tradeId);
    }
}
