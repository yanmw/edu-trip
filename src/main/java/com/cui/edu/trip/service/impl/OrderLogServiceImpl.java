package com.cui.edu.trip.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cui.edu.trip.entity.OrderLog;
import com.cui.edu.trip.mapper.OrderLogMapper;
import com.cui.edu.trip.service.OrderLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * <p>
 * 订单日志表 服务实现类
 * </p>
 */
@Slf4j
@Service
public class OrderLogServiceImpl extends ServiceImpl<OrderLogMapper, OrderLog> implements OrderLogService {

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveLog(OrderLog orderLog) {
        try {
            super.save(orderLog);
        } catch (Exception e) {
            log.error("订单日志保存失败，订单号：{}，业务动作：{}，异常：{}",
                    orderLog == null ? null : orderLog.getOrderNo(),
                    orderLog == null ? null : orderLog.getBizAction(),
                    e.getMessage(), e);
        }
    }
}
