package com.campus.growth.modules.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.growth.modules.order.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单明细 Mapper。
 */
@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {
}
