package com.kjs.wuli3.it.order.infra.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供集成测试订单表的 MyBatis-Plus 操作。
 *
 * @author 国杨 create on 2026/9/30 10:00
 */
@Mapper
public interface OrderMapper extends BaseMapper<OrderPo> {}
