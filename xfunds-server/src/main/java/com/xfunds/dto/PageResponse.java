package com.xfunds.dto;

import lombok.Data;

import java.util.List;

/**
 * 分页响应 DTO
 */
@Data
public class PageResponse<T> {

    /** 总记录数 */
    private Long total;
    /** 当前页码 */
    private Integer pageNum;
    /** 每页大小 */
    private Integer pageSize;
    /** 数据列表 */
    private List<T> list;

    /**
     * 构造分页响应
     */
    public PageResponse(Long total, Integer pageNum, Integer pageSize, List<T> list) {
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.list = list;
    }
}
