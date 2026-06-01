package com.cui.edu.trip.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cui.edu.trip.entity.OrderDetail;
import com.cui.edu.trip.mapper.OrderDetailMapper;
import com.cui.edu.trip.service.OrderDetailService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * <p>
 * 订单详情表 服务实现类
 * </p>
 *
 * @author Cuicui
 * @since 2026-05-29
 */
@Service
public class OrderDetailServiceImpl extends ServiceImpl<OrderDetailMapper, OrderDetail> implements OrderDetailService {

    @Override
    public List<OrderDetail> findByOrderId(String orderId) {
        QueryWrapper<OrderDetail> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(OrderDetail.ORDER_ID, orderId);
        return super.list(queryWrapper);
    }
}
