package com.xfunds.service;

import com.xfunds.dto.TaskVO;
import com.xfunds.entity.FxTask;

import java.util.List;

/**
 * 任务服务接口
 */
public interface TaskService {

    /**
     * 创建待处理任务
     *
     * @param taskType          任务类型
     * @param tradeId           交易ID
     * @param businessKey       业务键
     * @param businessType      业务类型
     * @param assigneeRole      指派角色
     * @param assigneeOrg       指派机构
     * @param assigneeOrgLevel  指派机构层级
     * @param payload           任务载荷（JSON）
     * @return 任务ID
     */
    Long createTask(String taskType, String tradeId, String businessKey, String businessType,
                    String assigneeRole, String assigneeOrg, Integer assigneeOrgLevel, String payload);

    /**
     * 创建待处理任务（直接指派给指定用户）
     *
     * @param taskType          任务类型
     * @param tradeId           交易ID
     * @param businessKey       业务键
     * @param businessType      业务类型
     * @param assigneeId        指派人ID
     * @param assigneeOrg       指派机构
     * @param assigneeOrgLevel  指派机构层级
     * @param payload           任务载荷（JSON）
     * @return 任务ID
     */
    Long createTaskForUser(String taskType, String tradeId, String businessKey, String businessType,
                           Long assigneeId, String assigneeOrg, Integer assigneeOrgLevel, String payload);



    /**
     * 完成任务
     */
    void completeTask(Long taskId, Long userId);

    /**
     * 取消任务
     */
    void cancelTask(Long taskId, String remark);

    /**
     * 查询我的待办任务（待处理或已认领）
     */
    List<FxTask> getMyTasks(Long userId);

    /**
     * 查询指定角色与机构的待处理任务
     */
    List<FxTask> getRoleTasks(String roleCode, String orgCode);

    /**
     * 根据任务ID查询任务
     */
    FxTask getByTaskId(Long taskId);

    /**
     * 查询当前用户的待办任务视图列表（含交易主表关联信息）
     *
     * @param userId 用户ID
     * @return 任务视图列表
     */
    List<TaskVO> getMyTaskVOs(Long userId);

    /**
     * 查询当前用户可见的所有待办任务（包括同机构及上级机构的任务）
     *
     * @param userId 用户ID
     * @param orgCode 用户所在机构
     * @param roleCode 用户角色
     * @return 任务视图列表
     */
    List<TaskVO> getVisibleTaskVOs(Long userId, String orgCode, String roleCode);

    /**
     * 查询指定角色与机构的待处理任务视图列表（含交易主表关联信息）
     * 排除当前用户自己发起的任务（不能认领自己的任务）
     *
     * @param roleCode       角色编码
     * @param orgCode        机构编码
     * @param currentUserId  当前用户ID（用于排除自己发起的任务）
     * @return 任务视图列表
     */
    List<TaskVO> getRoleTaskVOs(String roleCode, String orgCode, Long currentUserId);
}
