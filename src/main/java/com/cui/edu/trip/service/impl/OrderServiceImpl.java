package com.cui.edu.trip.service.impl;

import com.cui.edu.trip.entity.Order;
import com.cui.edu.trip.mapper.OrderMapper;
import com.cui.edu.trip.service.OrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 订单表 服务实现类
 * </p>
 *
 * @author Cuicui
 * @since 2026-05-29
 */
@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

}
