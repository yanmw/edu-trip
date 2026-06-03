package com.cui.edu.trip.mapper;

import com.cui.edu.trip.entity.OrderDetail;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * <p>
 * 订单详情表 Mapper 接口
 * </p>
 *
 * @author Cuicui
 * @since 2026-05-29
 */
public interface OrderDetailMapper extends BaseMapper<OrderDetail> {

    /**
     * 统计指定预约日期、活动、场次已经占用的子订单数量。
     *
     * @param museumId           博物馆ID
     * @param activityId         活动ID
     * @param activityScheduleId 活动场次ID
     * @param appointmentDate    预约日期
     * @param orderStatuses      会占用名额的主订单状态
     * @param detailStatuses     会占用名额的子订单状态
     * @return 已占用名额数量
     */
    int countBookedQuantity(@Param("museumId") Long museumId,
                            @Param("activityId") Long activityId,
                            @Param("activityScheduleId") Long activityScheduleId,
                            @Param("appointmentDate") LocalDate appointmentDate,
                            @Param("orderStatuses") List<Integer> orderStatuses,
                            @Param("detailStatuses") List<Integer> detailStatuses);

}
