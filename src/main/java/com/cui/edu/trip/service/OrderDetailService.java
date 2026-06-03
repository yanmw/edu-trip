package com.cui.edu.trip.service;

import com.cui.edu.trip.entity.OrderDetail;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDate;
import java.util.List;

/**
 * <p>
 * 订单详情表 服务类
 * </p>
 *
 * @author Cuicui
 * @since 2026-05-29
 */
public interface OrderDetailService extends IService<OrderDetail> {

    List<OrderDetail> findByOrderId(String orderId);

    /**
     * 统计指定预约日期、活动、场次已经占用的名额数量。
     *
     * @param museumId           博物馆ID
     * @param activityId         活动ID
     * @param activityScheduleId 活动场次ID
     * @param appointmentDate    预约日期
     * @return 已占用名额数量
     */
    int countBookedQuantity(Long museumId, Long activityId, Long activityScheduleId, LocalDate appointmentDate);
}
