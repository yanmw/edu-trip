package com.cui.edu.trip.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cui.edu.trip.entity.OrderLog;

/**
 * <p>
 * 订单日志表 服务类
 * </p>
 */
public interface OrderLogService extends IService<OrderLog> {

    /**
     * 保存订单日志。
     *
     * <p>日志用于问题追踪，不能影响主业务流程。</p>
     *
     * @param orderLog 订单日志
     */
    void saveLog(OrderLog orderLog);
}
