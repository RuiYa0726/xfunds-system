package com.xfunds.service.impl;

import com.xfunds.common.Constants;
import com.xfunds.common.SecurityUtils;
import com.xfunds.dto.OrgSaveRequest;
import com.xfunds.dto.OrgTreeVO;
import com.xfunds.entity.FxOrg;
import com.xfunds.mapper.FxOrgMapper;
import com.xfunds.service.FxOrgService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 机构服务实现类
 */
@Service
public class FxOrgServiceImpl implements FxOrgService {

    @Autowired
    private FxOrgMapper fxOrgMapper;

    /**
     * 根据机构编码查询机构
     */
    @Override
    public FxOrg getByOrgCode(String orgCode) {
        return fxOrgMapper.selectByOrgCode(orgCode);
    }

    /**
     * 查询所有机构（扁平列表）
     */
    @Override
    public List<FxOrg> listAll() {
        return fxOrgMapper.selectAll();
    }

    /**
     * 查询机构树形结构：从扁平列表构建树，根节点为 parentOrgCode 为空的机构
     */
    @Override
    public List<OrgTreeVO> listTree() {
        List<FxOrg> orgList = fxOrgMapper.selectAll();
        // 将机构实体转换为树节点
        Map<String, OrgTreeVO> nodeMap = new HashMap<>();
        for (FxOrg org : orgList) {
            OrgTreeVO node = convertToTreeVO(org);
            nodeMap.put(node.getOrgCode(), node);
        }
        // 构建树形结构：遍历节点，将子节点挂到父节点的 children 列表
        List<OrgTreeVO> rootNodes = new ArrayList<>();
        for (OrgTreeVO node : nodeMap.values()) {
            if (node.getParentOrgCode() == null || node.getParentOrgCode().isEmpty()) {
                rootNodes.add(node);
            } else {
                OrgTreeVO parentNode = nodeMap.get(node.getParentOrgCode());
                if (parentNode != null) {
                    if (parentNode.getChildren() == null) {
                        parentNode.setChildren(new ArrayList<>());
                    }
                    parentNode.getChildren().add(node);
                } else {
                    // 父节点不存在时作为根节点处理
                    rootNodes.add(node);
                }
            }
        }
        return rootNodes;
    }

    /**
     * 查询当前登录用户所属机构及其所有下级机构（含自身）
     * 若当前用户未登录或未配置机构，返回空列表
     */
    @Override
    public List<FxOrg> listMyOrgAndChildren() {
        String orgCode = SecurityUtils.getCurrentOrgCode();
        if (orgCode == null || orgCode.isEmpty()) {
            return new ArrayList<>();
        }
        List<FxOrg> result = fxOrgMapper.selectOrgAndChildren(orgCode);
        return result != null ? result : new ArrayList<>();
    }

    /**
     * 新增或更新机构
     */
    @Override
    public void saveOrg(OrgSaveRequest request) {
        FxOrg org = new FxOrg();
        org.setOrgCode(request.getOrgCode());
        org.setOrgName(request.getOrgName());
        org.setOrgLevel(request.getOrgLevel());
        org.setParentOrgCode(request.getParentOrgCode());
        org.setOrgType(request.getOrgType());
        org.setIsTradingOrg(request.getIsTradingOrg() != null ? request.getIsTradingOrg() : Constants.YES);
        org.setApprovalLimit(request.getApprovalLimit());
        org.setFxBusinessFlag(request.getFxBusinessFlag() != null ? request.getFxBusinessFlag() : Constants.YES);
        org.setStatus(request.getStatus() != null ? request.getStatus() : Constants.STATUS_ACTIVE);

        // 判断新增或更新
        FxOrg existOrg = fxOrgMapper.selectByOrgCode(request.getOrgCode());
        if (existOrg == null) {
            fxOrgMapper.insert(org);
        } else {
            fxOrgMapper.update(org);
        }
    }

    /**
     * 将机构实体转换为树节点视图对象
     */
    private OrgTreeVO convertToTreeVO(FxOrg org) {
        OrgTreeVO vo = new OrgTreeVO();
        vo.setOrgCode(org.getOrgCode());
        vo.setOrgName(org.getOrgName());
        vo.setOrgLevel(org.getOrgLevel());
        vo.setParentOrgCode(org.getParentOrgCode());
        vo.setApprovalLimit(org.getApprovalLimit());
        vo.setStatus(org.getStatus());
        return vo;
    }
}
