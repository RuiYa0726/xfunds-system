package com.xfunds.mapper;

import com.xfunds.entity.FxScheduledJobDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 定时任务执行明细 Mapper 接口
 */
@Mapper
public interface FxScheduledJobDetailMapper {

    /**
     * 批量新增执行明细
     */
    int batchInsert(@Param("list") List<FxScheduledJobDetail> list);

    /**
     * 根据日志ID查询执行明细列表
     */
    List<FxScheduledJobDetail> selectByLogId(@Param("logId") Long logId);
}
