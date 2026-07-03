package com.xfunds.service.impl;

import com.xfunds.entity.FxSysParam;
import com.xfunds.mapper.FxSysParamMapper;
import com.xfunds.service.SysParamService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 系统参数服务实现类
 */
@Service
public class SysParamServiceImpl implements SysParamService {

    /** 业务日期参数编码 */
    private static final String PARAM_CODE_BUSINESS_DATE = "BUSINESS_DATE";

    @Autowired
    private FxSysParamMapper fxSysParamMapper;

    /**
     * 查询所有系统参数
     */
    @Override
    public List<FxSysParam> listAll() {
        return fxSysParamMapper.selectAll();
    }

    /**
     * 更新系统参数
     */
    @Override
    public void save(FxSysParam param) {
        fxSysParamMapper.update(param);
    }

    /**
     * 获取当前系统业务日期：从 fx_sys_param 读取 BUSINESS_DATE，未配置时默认返回当天
     */
    @Override
    public LocalDate getBusinessDate() {
        FxSysParam param = fxSysParamMapper.selectByParamCode(PARAM_CODE_BUSINESS_DATE);
        if (param != null && param.getParamValue() != null && !param.getParamValue().isEmpty()) {
            return LocalDate.parse(param.getParamValue());
        }
        // 未配置时默认返回当天日期
        return LocalDate.now();
    }

    /**
     * 更新系统业务日期：若 BUSINESS_DATE 参数不存在则新增，存在则更新
     */
    @Override
    public void updateBusinessDate(LocalDate date) {
        FxSysParam param = fxSysParamMapper.selectByParamCode(PARAM_CODE_BUSINESS_DATE);
        if (param == null) {
            // 参数不存在时新增
            param = new FxSysParam();
            param.setParamCode(PARAM_CODE_BUSINESS_DATE);
            param.setParamName("系统业务日期");
            param.setParamValue(date.toString());
            param.setParamType("DATE");
            param.setDescription("当前系统业务日期");
            fxSysParamMapper.insert(param);
        } else {
            // 参数存在时更新
            param.setParamValue(date.toString());
            fxSysParamMapper.update(param);
        }
    }
}
