package com.xfunds.mapper;

import com.xfunds.entity.ReportQueryAuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 报表查询审计日志 Mapper
 */
@Mapper
public interface ReportQueryAuditLogMapper {

    /** 插入审计日志 */
    int insert(ReportQueryAuditLog log);
}
