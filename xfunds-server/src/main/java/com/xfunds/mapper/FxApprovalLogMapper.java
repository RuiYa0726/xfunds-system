package com.xfunds.mapper;

import com.xfunds.entity.FxApprovalLog;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 审批日志 Mapper 接口
 */
@Mapper
public interface FxApprovalLogMapper {

    /**
     * 根据交易ID查询审批日志列表
     */
    List<FxApprovalLog> selectByTradeId(String tradeId);

    /**
     * 新增审批日志
     */
    int insert(FxApprovalLog approvalLog);
}
