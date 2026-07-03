package com.xfunds.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 机构树形视图对象 DTO
 */
@Data
public class OrgTreeVO {

    /** 机构编码 */
    private String orgCode;

    /** 机构名称 */
    private String orgName;

    /** 机构层级：1总行 2分行 3支行 */
    private Integer orgLevel;

    /** 上级机构编码 */
    private String parentOrgCode;

    /** 审批权限额度 */
    private BigDecimal approvalLimit;

    /** 状态：1启用 0停用 */
    private String status;

    /** 子机构列表 */
    private List<OrgTreeVO> children;
}
