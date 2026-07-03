package com.xfunds.service;

import com.xfunds.entity.FxApprovalLog;

import java.util.List;

/**
 * 审批日志服务接口
 */
public interface ApprovalLogService {

    /**
     * 记录审批日志
     *
     * @param tradeId       交易ID
     * @param node          审批节点：MAKE/CHECK/AUTHORIZE
     * @param approverId    审批人ID
     * @param approverName  审批人姓名
     * @param approverOrg   审批人机构
     * @param decision      决定：APPROVE/REJECT/RETURN
     * @param comment       审批意见
     * @param beforeStatus  变更前状态
     * @param afterStatus   变更后状态
     */
    void recordLog(String tradeId, String node, Long approverId, String approverName,
                   String approverOrg, String decision, String comment,
                   String beforeStatus, String afterStatus);

    /**
     * 根据交易ID查询审批日志列表
     */
    List<FxApprovalLog> listByTradeId(String tradeId);
}
