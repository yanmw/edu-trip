package com.cui.edu.trip.service;

import com.cui.edu.trip.entity.OrderDetail;
import com.baomidou.mybatisplus.extension.service.IService;

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
}
