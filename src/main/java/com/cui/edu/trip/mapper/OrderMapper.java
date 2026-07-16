package com.cui.edu.trip.mapper;

import com.cui.edu.trip.entity.Order;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 订单表 Mapper 接口
 * </p>
 *
 * @author Cuicui
 * @since 2026-05-29
 */
public interface OrderMapper extends BaseMapper<Order> {

    List<Map> findNumAndAmount(@Param("museumId") String museumId, @Param("currentMonthStart") String currentMonthStart, @Param("currentMonthEnd")String currentMonthEnd);

    List<String> findVerificationTradeNo(String museumId, String currentMonthStart, String currentMonthEnd);

    /**
     * 查询核对月内支付成功、但预约日期在下月的订单（银联，返回trade_no）
     * 场景：6月下单支付，预约的是7月，6月银联有流水但系统核销在7月，属于6月异常数据
     */
    List<String> findCrossMonthAppointmentTradeNo(@Param("museumId") String museumId,
                                                  @Param("currentMonthStart") String currentMonthStart,
                                                  @Param("currentMonthEnd") String currentMonthEnd);
}
