package com.xfunds.mapper;

import com.xfunds.entity.FxTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 任务 Mapper 接口
 */
@Mapper
public interface FxTaskMapper {

    /**
     * 根据任务ID查询任务
     */
    FxTask selectByTaskId(Long taskId);

    /**
     * 根据状态查询任务列表
     */
    List<FxTask> selectByStatus(String status);

    /**
     * 根据指派人ID查询任务列表
     */
    List<FxTask> selectByAssigneeId(Long assigneeId);

    /**
     * 根据指派人ID查询任务列表（限定状态为待处理或已认领）
     */
    List<FxTask> selectActiveByAssigneeId(Long assigneeId);

    /**
     * 根据指派角色与机构查询待处理任务列表
     */
    List<FxTask> selectByRoleAndOrg(@Param("assigneeRole") String assigneeRole,
                                    @Param("assigneeOrg") String assigneeOrg);

    /**
     * 根据指派角色查询所有待处理任务列表（不限机构，供系统管理员跨机构使用）
     */
    List<FxTask> selectByRole(@Param("assigneeRole") String assigneeRole);

    /**
     * 根据角色和机构列表查询待办任务（包括同机构及上级机构）
     */
    List<FxTask> selectByRoleAndOrgList(@Param("assigneeRole") String assigneeRole,
                                        @Param("orgCodes") List<String> orgCodes);

    /**
     * 根据交易ID和业务类型查询待处理任务（状态为 PENDING 或 CLAIMED）
     * 用于防止同一交易的同一生命周期操作重复提交
     */
    FxTask selectPendingByTradeIdAndBusinessType(@Param("tradeId") String tradeId,
                                                  @Param("businessType") String businessType);

    /**
     * 新增任务
     */
    int insert(FxTask task);

    /**
     * 更新任务
     */
    int update(FxTask task);
}
