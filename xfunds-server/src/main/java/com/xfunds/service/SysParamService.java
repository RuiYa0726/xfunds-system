package com.xfunds.service;

import com.xfunds.entity.FxSysParam;

import java.time.LocalDate;
import java.util.List;

/**
 * 系统参数服务接口
 */
public interface SysParamService {

    /**
     * 查询所有系统参数
     */
    List<FxSysParam> listAll();

    /**
     * 更新系统参数
     */
    void save(FxSysParam param);

    /**
     * 获取当前系统业务日期
     */
    LocalDate getBusinessDate();

    /**
     * 更新系统业务日期
     */
    void updateBusinessDate(LocalDate date);
}
