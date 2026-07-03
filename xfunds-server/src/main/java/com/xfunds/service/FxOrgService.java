package com.xfunds.service;

import com.xfunds.dto.OrgSaveRequest;
import com.xfunds.dto.OrgTreeVO;
import com.xfunds.entity.FxOrg;

import java.util.List;

/**
 * 机构服务接口
 */
public interface FxOrgService {

    /**
     * 根据机构编码查询机构
     */
    FxOrg getByOrgCode(String orgCode);

    /**
     * 查询所有机构（扁平列表）
     */
    List<FxOrg> listAll();

    /**
     * 查询机构树形结构
     */
    List<OrgTreeVO> listTree();

    /**
     * 查询当前登录用户所属机构及其所有下级机构（含自身）
     * 若当前用户未登录或未配置机构，返回空列表
     */
    List<FxOrg> listMyOrgAndChildren();

    /**
     * 新增或更新机构
     */
    void saveOrg(OrgSaveRequest request);
}
