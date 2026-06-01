package com.cui.edu.config.redis;


import com.alibaba.fastjson2.JSONObject;

import com.cui.edu.common.SysConstants;
import com.cui.edu.trip.service.OrderService;
import com.cui.edu.trip.service.UnionPayService;
import com.cui.edu.util.RedisUtils;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Cuicui
 * @description: 消息处理器
 */
@Component
@Slf4j
public class RedisMessageReceiverConfig {

    Lock l = new ReentrantLock();

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private OrderService orderService;

    @Autowired
    private UnionPayService unionPayService;


    @Autowired
    private ScheduledExecutorService scheduledExecutorService;


    /**
     * 订阅mq_union_refund_query信道的消息
     * 银联退款调用成功后，进入此队列，主动调银联查询退款详情，jsonObject中的数据均由创建队列时所传
     **/
    public void receiveMessageUnionRefundQuery(Object message) {
        log.info("receiveMessageUnionRefundQuery接收到的消息：" + message);
        Boolean b = redisUtils.setIfAbsent(SysConstants.SET_NX + "UnionRefund", String.valueOf(System.currentTimeMillis()), 3);
        if (b) {
            scheduledExecutorService.schedule(() -> {
                try {
                    unionRefundQuery(message);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, 600, TimeUnit.SECONDS);
        }
    }

    public void unionRefundQuery(Object message) throws Exception {
        // 手动反序列化redis数据
        ObjectMapper objectMapper = new ObjectMapper();
        // 以忽略未知的JSON字段
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // 反序列化为指定的Java对象类型
        JSONObject jsonObject = objectMapper.readValue(message.toString(), JSONObject.class);
        String refundOrderId = jsonObject.getString("refundOrderId");
        String orderNo = jsonObject.getString("orderNo");
        String mid = jsonObject.getString("mid");
        String tid = jsonObject.getString("tid");
        Integer money = jsonObject.getInteger("money");
        JSONObject result = unionPayService.appletRefundQuery(refundOrderId, mid, tid);
        if (result != null && SysConstants.SUCCESS.equals(result.getString("errCode"))) {
            // 银联订单号
            String tradeNo = result.getString("targetOrderId");
            // 银联交易流水号
            String flowNo = result.getString("seqId");
            // 退款订单号
            String refundOrderNo = result.getString("refundOrderId");
            // 报文响应时间
            String refundTime = result.getString("responseTimestamp");
            orderService.unionRefundNotify(orderNo, tradeNo, money, refundOrderNo, refundTime, result.toString());
        }
    }


}
