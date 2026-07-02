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

    /**
     * 根据主订单 ID 查询其下关联的所有子订单（订单详情）列表
     *
     * @param orderId 主订单 ID
     * @return 该主订单下的所有子订单详情列表
     */
    @Override
    public List<OrderDetail> findByOrderId(String orderId) {
        // 构造查询 Wrapper，按主订单 ID 进行过滤
        QueryWrapper<OrderDetail> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(OrderDetail.ORDER_ID, orderId);
        return super.list(queryWrapper);
    }

    /**
     * 统计指定场次在特定日期下已经被占用的总名额数
     * <p>
     * 业务规则：
     * 1. 该统计用于在下单时判断余票/剩余名额是否充足，避免超卖。
     * 2. 只统计满足 BOOKED_ORDER_STATUSES（占名额的主订单状态）和 BOOKED_DETAIL_STATUSES（占名额的子订单状态）的订单。
     *
     * @param museumId           博物馆 ID
     * @param activityId         活动 ID
     * @param activityScheduleId 活动场次排期 ID
     * @param appointmentDate    预约日期
     * @return 已占用的名额总数 (通常等于已预订的门票/游客数量)
     */
    @Override
    public int countBookedQuantity(Long museumId, Long activityId, Long activityScheduleId, LocalDate appointmentDate) {
        // 传入有效的主订单和子订单状态集合，委托 mapper 执行 count 聚合查询
        return baseMapper.countBookedQuantity(museumId, activityId, activityScheduleId, appointmentDate,
                BOOKED_ORDER_STATUSES, BOOKED_DETAIL_STATUSES);
    }
}
