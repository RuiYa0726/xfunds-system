package com.xfunds.mapper;

import com.xfunds.entity.FxScheduledJobLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 定时任务执行日志 Mapper 接口
 */
@Mapper
public interface FxScheduledJobLogMapper {

    /**
     * 新增执行日志
     */
    int insert(FxScheduledJobLog log);

    /**
     * 分页查询执行日志（jobName 为空时查询全部）
     */
    List<FxScheduledJobLog> selectByCondition(@Param("jobName") String jobName,
                                              @Param("offset") int offset,
                                              @Param("pageSize") int pageSize);

    /**
     * 查询执行日志总数（jobName 为空时统计全部）
     */
    long countByCondition(@Param("jobName") String jobName);

    /**
     * 查询指定任务的最近一次执行日志
     */
    FxScheduledJobLog selectLatestByJobName(@Param("jobName") String jobName);
}
