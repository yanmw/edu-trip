package com.cui.edu.config.redis;


import com.alibaba.fastjson2.JSONObject;

import com.cui.edu.common.SysConstants;
import com.cui.edu.trip.entity.OrderLog;
import com.cui.edu.trip.service.OrderService;
import com.cui.edu.trip.service.UnionPayService;
import com.cui.edu.util.RedisUtils;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.SocketTimeoutException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author Cuicui
 * @description: 消息处理器
 */
@Component
@Slf4j
public class RedisMessageReceiverConfig {

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
        // 退款申请成功后延迟10分钟查询银联，给银联异步退款处理留出时间。
        scheduledExecutorService.schedule(() -> {
            try {
                unionRefundQuery(message);
            } catch (SocketTimeoutException e) {
                // 网络超时属于临时故障，重新投递消息到队列对下一轮查询，防止订单长期停留在退款中状态
                log.warn("银联退款查询请求超时，重新投递消息等待下一轮延迟查询，消息：{}", message, e);
                try {
                    redisUtils.convertAndSend("mq_union_refund_query", message);
                } catch (Exception re) {
                    log.error("重新投递银联退款查询消息失败，消息：{}", message, re);
                }
            } catch (Exception e) {
                log.error("银联退款查询补偿处理失败，消息：{}", message, e);
            }
        }, 600, TimeUnit.SECONDS);
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
        // 主动查询银联退款状态；查询结果无论是否修改订单，都会写入订单日志。
        JSONObject result = unionPayService.appletRefundQuery(refundOrderId, mid, tid);
        orderService.recordUnionRefundQueryLog(orderNo, refundOrderId, money, result,
                OrderLog.SOURCE_REDIS_REFUND_QUERY, "Redis退款补偿查询银联退款状态");
        if (result == null) {
            log.warn("银联退款查询无结果，退款单号：{}", refundOrderId);
            // 银联无响应时重新投递消息，等待下一轮延迟查询。
            redisUtils.convertAndSend("mq_union_refund_query", jsonObject);
            return;
        }
        if (!SysConstants.SUCCESS.equals(result.getString("errCode"))) {
            log.warn("银联退款查询失败，退款单号：{}，查询结果：{}", refundOrderId, result);
            return;
        }

        String refundStatus = result.getString("refundStatus");
        if (SysConstants.REFUND_SUCCESS.equals(refundStatus)) {
            // 银联确认退款成功时，复用退款回调逻辑更新本地主子订单状态。
            // 银联订单号
            String tradeNo = result.getString("targetOrderId");
            // 退款订单号和金额以银联查询结果为准；若银联未返回，则使用原补偿消息中的值兜底。
            String refundOrderNo = result.getString("refundOrderId");
            if (refundOrderNo == null || refundOrderNo.length() == 0) {
                refundOrderNo = refundOrderId;
            }
            Integer refundAmount = result.getInteger("refundAmount");
            if (refundAmount == null) {
                refundAmount = result.getInteger("totalAmount");
            }
            if (refundAmount == null) {
                refundAmount = money;
            }
            // 退款完成时间优先取银联退款支付时间，缺失时再用响应时间。
            String refundTime = result.getString("refundPayTime");
            if (refundTime == null || refundTime.length() == 0) {
                refundTime = result.getString("responseTimestamp");
            }
            orderService.unionRefundNotify(orderNo, tradeNo, refundAmount, refundOrderNo, refundTime, result.toString());
            return;
        }

        if (SysConstants.REFUND_PROCESSING.equals(refundStatus) || SysConstants.UNKNOWN.equals(refundStatus)) {
            log.info("银联退款仍在处理中，稍后继续查询，退款单号：{}，状态：{}", refundOrderId, refundStatus);
            // 处理中或未知状态继续投递队列，避免退款回调丢失造成订单长期停在退款中。
            redisUtils.convertAndSend("mq_union_refund_query", jsonObject);
            return;
        }

        if (SysConstants.REFUND_FAIL.equals(refundStatus)) {
            log.warn("银联退款失败，开始回退本地退款中状态，退款单号：{}，查询结果：{}", refundOrderId, result);
            // 银联明确退款失败时，回退本地退款中状态，恢复可再次申请退款的支付成功状态。
            orderService.handleRefundQueryFailed(orderNo, refundOrderId);
            return;
        }

        log.warn("银联退款查询返回未知退款状态，退款单号：{}，查询结果：{}", refundOrderId, result);
    }


}
