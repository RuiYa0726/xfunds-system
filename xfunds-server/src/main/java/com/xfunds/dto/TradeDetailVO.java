package com.xfunds.dto;

import com.xfunds.entity.FxApprovalLog;
import com.xfunds.entity.FxTradeLifecycle;
import com.xfunds.entity.FxTradeMaster;
import lombok.Data;

import java.util.List;

/**
 * 交易详情 VO（包含主表信息、子表明细、生命周期事件、审批日志）
 */
@Data
public class TradeDetailVO {

    /** 交易主表信息 */
    private FxTradeMaster master;

    /** 经办人姓名 */
    private String makerName;

    /** 复核人姓名 */
    private String checkerName;

    /** 即期交易明细（tradeType=SPOT 时有值） */
    private Object spotDetail;

    /** 远期交易明细（tradeType=FORWARD 时有值） */
    private Object forwardDetail;

    /** 掉期交易明细（tradeType=SWAP 时有值） */
    private Object swapDetail;

    /** 生命周期事件列表 */
    private List<FxTradeLifecycle> lifecycleList;

    /** 审批日志列表 */
    private List<FxApprovalLog> approvalLogList;
}
