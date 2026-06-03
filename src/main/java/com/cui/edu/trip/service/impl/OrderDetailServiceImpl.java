package com.cui.edu.trip.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cui.edu.trip.entity.Order;
import com.cui.edu.trip.entity.OrderDetail;
import com.cui.edu.trip.mapper.OrderDetailMapper;
import com.cui.edu.trip.service.OrderDetailService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Arrays;
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

    /**
     * 会占用活动场次名额的主订单状态：待支付订单会保留 15 分钟名额，退款中的订单在退款成功前仍占位。
     */
    private static final List<Integer> BOOKED_ORDER_STATUSES = Arrays.asList(
            Order.OrderStatusEnum.PAYING.getValue(),
            Order.OrderStatusEnum.SUCCESS.getValue(),
            Order.OrderStatusEnum.PARTIAL_REFUND.getValue(),
            Order.OrderStatusEnum.REFUNDING.getValue()
    );

    /**
     * 会占用活动场次名额的子订单状态：初始待支付、支付成功、退款中均算占位。
     */
    private static final List<Integer> BOOKED_DETAIL_STATUSES = Arrays.asList(
            OrderDetail.OrderDetailStatusEnum.INIT.getValue(),
            OrderDetail.OrderDetailStatusEnum.PAY_SUCCESS.getValue(),
            OrderDetail.OrderDetailStatusEnum.REFUNDING.getValue()
    );

    @Override
    public List<OrderDetail> findByOrderId(String orderId) {
        QueryWrapper<OrderDetail> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(OrderDetail.ORDER_ID, orderId);
        return super.list(queryWrapper);
    }

    @Override
    public int countBookedQuantity(Long museumId, Long activityId, Long activityScheduleId, LocalDate appointmentDate) {
        // 统计已占用名额时，只统计还会占用场次容量的主订单和子订单状态。
        return baseMapper.countBookedQuantity(museumId, activityId, activityScheduleId, appointmentDate,
                BOOKED_ORDER_STATUSES, BOOKED_DETAIL_STATUSES);
    }
}
