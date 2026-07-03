package com.xfunds.mapper;

import com.xfunds.entity.FxCustomer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 客户 Mapper 接口
 */
@Mapper
public interface FxCustomerMapper {

    /**
     * 根据客户ID查询客户
     */
    FxCustomer selectByCustomerId(String customerId);

    /**
     * 查询所有客户
     */
    List<FxCustomer> selectAll();

    /**
     * 新增客户
     */
    int insert(FxCustomer fxCustomer);

    /**
     * 更新客户
     */
    int update(FxCustomer fxCustomer);

    /**
     * 按关键字模糊查询客户（客户ID或客户名称）
     */
    List<FxCustomer> selectByKeyword(String keyword);

    /**
     * 按条件分页查询客户列表
     * 客户号模糊匹配，客户类型精确匹配
     */
    List<FxCustomer> selectByCondition(@Param("customerId") String customerId,
                                       @Param("customerName") String customerName,
                                       @Param("customerType") String customerType,
                                       @Param("orgCode") String orgCode,
                                       @Param("offset") int offset, @Param("pageSize") int pageSize);

    /**
     * 按条件查询客户总数
     * 客户号模糊匹配，客户类型精确匹配
     */
    long countByCondition(@Param("customerId") String customerId,
                          @Param("customerName") String customerName,
                          @Param("customerType") String customerType,
                          @Param("orgCode") String orgCode);
}
