package com.xfunds.service.impl;

import com.xfunds.common.BusinessException;
import com.xfunds.common.ResultCode;
import com.xfunds.dto.TaskVO;
import com.xfunds.entity.FxOrg;
import com.xfunds.entity.FxTask;
import com.xfunds.entity.FxTradeMaster;
import com.xfunds.entity.FxUser;
import com.xfunds.enums.TaskStatus;
import com.xfunds.mapper.FxOrgMapper;
import com.xfunds.mapper.FxTaskMapper;
import com.xfunds.mapper.FxTradeMasterMapper;
import com.xfunds.mapper.FxUserMapper;
import com.xfunds.service.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 任务服务实现类
 */
@Service
public class TaskServiceImpl implements TaskService {

    @Autowired
    private FxTaskMapper fxTaskMapper;

    @Autowired
    private FxTradeMasterMapper fxTradeMasterMapper;

    @Autowired
    private FxUserMapper fxUserMapper;

    @Autowired
    private FxOrgMapper fxOrgMapper;

    /**
     * 创建待处理任务
     */
    @Override
    public Long createTask(String taskType, String tradeId, String businessKey, String businessType,
                           String assigneeRole, String assigneeOrg, Integer assigneeOrgLevel, String payload) {
        FxTask task = new FxTask();
        task.setTaskType(taskType);
        task.setTradeId(tradeId);
        task.setBusinessKey(businessKey);
        task.setBusinessType(businessType);
        task.setPriority(0);
        task.setAssigneeRole(assigneeRole);
        task.setAssigneeOrg(assigneeOrg);
        task.setAssigneeOrgLevel(assigneeOrgLevel);
        task.setStatus(TaskStatus.PENDING.name());
        task.setPayload(payload);
        fxTaskMapper.insert(task);
        return task.getTaskId();
    }

    /**
     * 创建待处理任务（直接指派给指定用户）
     */
    @Override
    public Long createTaskForUser(String taskType, String tradeId, String businessKey, String businessType,
                                  Long assigneeId, String assigneeOrg, Integer assigneeOrgLevel, String payload) {
        FxTask task = new FxTask();
        task.setTaskType(taskType);
        task.setTradeId(tradeId);
        task.setBusinessKey(businessKey);
        task.setBusinessType(businessType);
        task.setPriority(0);
        task.setAssigneeId(assigneeId);
        task.setAssigneeOrg(assigneeOrg);
        task.setAssigneeOrgLevel(assigneeOrgLevel);
        task.setStatus(TaskStatus.PENDING.name());
        task.setPayload(payload);
        fxTaskMapper.insert(task);
        return task.getTaskId();
    }



    /**
     * 完成任务：设置状态为已完成、记录完成时间
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeTask(Long taskId, Long userId) {
        FxTask task = getTaskOrThrow(taskId);
        if (TaskStatus.DONE.name().equals(task.getStatus()) || TaskStatus.CANCELLED.name().equals(task.getStatus())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "任务已完成或已取消，无法重复操作");
        }
        task.setAssigneeId(userId);
        task.setStatus(TaskStatus.DONE.name());
        task.setCompleteTime(LocalDateTime.now());
        fxTaskMapper.update(task);
    }

    /**
     * 取消任务：设置状态为已取消
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelTask(Long taskId, String remark) {
        FxTask task = getTaskOrThrow(taskId);
        task.setStatus(TaskStatus.CANCELLED.name());
        fxTaskMapper.update(task);
    }

    /**
     * 查询我的待办任务（待处理或已认领）
     */
    @Override
    public List<FxTask> getMyTasks(Long userId) {
        return fxTaskMapper.selectActiveByAssigneeId(userId);
    }

    /**
     * 查询指定角色与机构的待处理任务
     */
    @Override
    public List<FxTask> getRoleTasks(String roleCode, String orgCode) {
        return fxTaskMapper.selectByRoleAndOrg(roleCode, orgCode);
    }

    /**
     * 根据任务ID查询任务
     */
    @Override
    public FxTask getByTaskId(Long taskId) {
        return fxTaskMapper.selectByTaskId(taskId);
    }

    /**
     * 查询当前用户的待办任务视图列表：关联交易主表获取业务编号与交易类型，关联用户表获取经办人姓名
     */
    @Override
    public List<TaskVO> getMyTaskVOs(Long userId) {
        List<FxTask> tasks = fxTaskMapper.selectActiveByAssigneeId(userId);
        return convertToVOList(tasks);
    }

    /**
     * 查询指定角色与机构的待处理任务视图列表：关联交易主表获取业务编号与交易类型
     * （不再排除当前用户自己发起的任务，因为同时拥有 MAKER 和 CHECKER 角色的用户可以处理自己的任务）
     */
    @Override
    public List<TaskVO> getRoleTaskVOs(String roleCode, String orgCode, Long currentUserId) {
        List<FxTask> tasks = fxTaskMapper.selectByRoleAndOrg(roleCode, orgCode);
        return convertToVOList(tasks);
    }

    /**
     * 查询当前用户可见的所有待办任务（包括同机构及上级机构的任务，以及指派给自己的任务）
     */
    @Override
    public List<TaskVO> getVisibleTaskVOs(Long userId, String orgCode, String roleCode) {
        // 1. 获取当前机构及其所有上级机构
        List<FxOrg> orgs = fxOrgMapper.selectOrgAndParents(orgCode);
        List<String> orgCodes = new ArrayList<>();
        for (FxOrg org : orgs) {
            orgCodes.add(org.getOrgCode());
        }

        // 2. 查询这些机构下的待办任务
        List<FxTask> tasks = fxTaskMapper.selectByRoleAndOrgList(roleCode, orgCodes);

        // 3. 查询指派给当前用户的任务
        List<FxTask> myTasks = fxTaskMapper.selectActiveByAssigneeId(userId);

        // 4. 合并两个列表，去重
        List<FxTask> allTasks = new ArrayList<>();
        // 先添加角色任务
        allTasks.addAll(tasks);
        // 再添加指派给自己的任务，避免重复
        for (FxTask myTask : myTasks) {
            boolean exists = allTasks.stream().anyMatch(t -> t.getTaskId().equals(myTask.getTaskId()));
            if (!exists) {
                allTasks.add(myTask);
            }
        }

        // 5. 转换为VO
        return convertToVOList(allTasks);
    }

    /**
     * 将任务实体列表转换为任务视图列表，补充交易主表与经办人信息
     */
    private List<TaskVO> convertToVOList(List<FxTask> tasks) {
        List<TaskVO> voList = new ArrayList<>();
        for (FxTask task : tasks) {
            TaskVO vo = new TaskVO();
            vo.setTaskId(task.getTaskId());
            vo.setTaskType(task.getTaskType());
            vo.setTradeId(task.getTradeId());
            vo.setBusinessKey(task.getBusinessKey());
            vo.setBusinessType(task.getBusinessType());
            vo.setStatus(task.getStatus());
            vo.setPriority(task.getPriority());
            vo.setCreateTime(task.getCreateTime());
            vo.setDueTime(task.getDueTime());
            vo.setAssigneeId(task.getAssigneeId());
            vo.setAssigneeRole(task.getAssigneeRole());
            vo.setPayload(task.getPayload());

            // 查询受理人姓名
            if (task.getAssigneeId() != null) {
                FxUser assignee = fxUserMapper.selectByUserId(task.getAssigneeId());
                if (assignee != null) {
                    vo.setAssigneeName(assignee.getRealName());
                }
            }

            // 关联交易主表补充业务编号与交易类型
            if (task.getTradeId() != null) {
                FxTradeMaster master = fxTradeMasterMapper.selectByTradeId(task.getTradeId());
                if (master != null) {
                    vo.setBusinessNo(master.getBusinessNo());
                    vo.setTradeType(master.getTradeType());
                    vo.setMakerId(master.getMakerId());
                    // 查询经办人姓名
                    if (master.getMakerId() != null) {
                        FxUser maker = fxUserMapper.selectByUserId(master.getMakerId());
                        if (maker != null) {
                            vo.setMakerName(maker.getRealName());
                        }
                    }
                }
            }
            voList.add(vo);
        }
        return voList;
    }

    /**
     * 根据任务ID查询任务，不存在则抛出业务异常
     */
    private FxTask getTaskOrThrow(Long taskId) {
        FxTask task = fxTaskMapper.selectByTaskId(taskId);
        if (task == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "任务不存在");
        }
        return task;
    }
}
