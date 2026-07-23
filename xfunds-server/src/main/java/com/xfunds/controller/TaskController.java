package com.xfunds.controller;

import com.xfunds.common.BusinessException;
import com.xfunds.common.Result;
import com.xfunds.common.ResultCode;
import com.xfunds.common.SecurityUtils;
import com.xfunds.dto.TaskCancelRequest;
import com.xfunds.dto.TaskCompleteRequest;
import com.xfunds.dto.TaskVO;
import com.xfunds.entity.FxTask;
import com.xfunds.entity.FxUser;
import com.xfunds.service.TaskService;
import com.xfunds.service.TradeLifecycleOpsService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 待办任务控制器
 */
@RestController
@RequestMapping("/api/task")
public class TaskController {

    @Autowired
    private TaskService taskService;

    @Autowired
    private TradeLifecycleOpsService tradeLifecycleOpsService;

    /**
     * 查询当前用户可见的所有待办任务（包括同机构及上级机构的任务）
     */
    @GetMapping("/my")
    public Result<List<TaskVO>> myTasks() {
        FxUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户未登录");
        }
        Long userId = currentUser.getUserId();
        String roleCode = determineRoleCode();
        String orgCode = currentUser.getOrgCode();

        return Result.ok(taskService.getVisibleTaskVOs(userId, orgCode, roleCode));
    }



    /**
     * 根据任务ID查询任务详情
     */
    @GetMapping("/{taskId}")
    public Result<FxTask> getTask(@PathVariable Long taskId) {
        return Result.ok(taskService.getByTaskId(taskId));
    }



    /**
     * 完成任务：根据处理结果调用对应的审批方法
     * result 为 APPROVE/REJECT/RETURN，分别对应复核通过/拒绝/退回
     */
    @PostMapping("/{taskId}/complete")
    public Result<Void> complete(@PathVariable Long taskId, @Valid @RequestBody TaskCompleteRequest request) {
        String result = request.getResult();
        String tradeId = request.getTradeId();
        String comment = request.getComment();

        if ("APPROVE".equals(result)) {
            tradeLifecycleOpsService.approveTrade(taskId, tradeId, comment);
        } else if ("REJECT".equals(result)) {
            tradeLifecycleOpsService.rejectTrade(taskId, tradeId, comment);
        } else if ("RETURN".equals(result)) {
            tradeLifecycleOpsService.returnTrade(taskId, tradeId, comment);
        } else {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的处理结果：" + result);
        }
        return Result.ok();
    }

    /**
     * 取消任务
     */
    @PostMapping("/{taskId}/cancel")
    public Result<Void> cancel(@PathVariable Long taskId, @RequestBody TaskCancelRequest request) {
        String remark = request != null ? request.getRemark() : null;
        taskService.cancelTask(taskId, remark);
        return Result.ok();
    }

    /**
     * 完成修改任务（仅标记任务完成，实际修改和重新提交由前端通过交易编辑页面处理）
     */
    @PostMapping("/{taskId}/complete-modify")
    public Result<Void> completeModify(@PathVariable Long taskId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        taskService.completeTask(taskId, currentUserId);
        return Result.ok();
    }

    /**
     * 确定当前用户的角色编码：优先复核角色，其次授权角色，最后经办角色
     */
    private String determineRoleCode() {
        if (SecurityUtils.hasRole("CHECKER")) {
            return "CHECKER";
        }
        if (SecurityUtils.hasRole("AUTHORIZER")) {
            return "AUTHORIZER";
        }
        return "MAKER";
    }
}
