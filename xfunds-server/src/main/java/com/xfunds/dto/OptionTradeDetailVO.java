package com.xfunds.dto;

import com.xfunds.entity.FxApprovalLog;
import com.xfunds.entity.FxOptionTrade;
import com.xfunds.entity.FxTradeLifecycle;
import com.xfunds.entity.FxTradeMaster;
import lombok.Data;

import java.util.List;

/**
 * 期权交易详情 VO（包含主表信息、期权子表明细、生命周期事件、审批日志）
 */
@Data
public class OptionTradeDetailVO {

    /** 交易主表信息 */
    private FxTradeMaster master;

    /** 期权交易明细 */
    private FxOptionTrade optionDetail;

    /** 生命周期事件列表 */
    private List<FxTradeLifecycle> lifecycleList;

    /** 审批日志列表 */
    private List<FxApprovalLog> approvalLogList;
}
