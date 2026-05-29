package com.cui.edu.config.redis;

import com.cui.edu.common.SysConstants;
import com.cui.edu.trip.service.OrderService;
import com.cui.edu.util.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.Topic;
import org.springframework.stereotype.Component;


/**
 * redis处理过期key值
 *
 * @author Cuicui
 */
@Component
@Slf4j
public class RedisListenerConfig extends KeyExpirationEventMessageListener {


    @Value("${spring.redis.database}")
    private Integer database;


    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private OrderService orderService;

    public RedisListenerConfig(RedisMessageListenerContainer listenerContainer) {
        super(listenerContainer);

    }

    /**
     * 只监听指定的数据库过期key
     *
     * @param listenerContainer
     */
    @Override
    protected void doRegister(RedisMessageListenerContainer listenerContainer) {
        Topic KEYEVENT_EXPIRED_TOPIC = new PatternTopic("__keyevent@" + database + "__:expired");
        listenerContainer.addMessageListener(this, KEYEVENT_EXPIRED_TOPIC);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        // 下单时写入Redis的key就是订单号，过期后用它做支付状态补偿。
        String expiredKey = message.toString();
        if (expiredKey.contains(SysConstants.SET_NX)) {
            log.info("锁，不再处理");
            return;
        }
        // 多实例部署时可能同时收到过期事件，用短期SETNX避免同一订单重复处理。
        Boolean b = redisUtils.setIfAbsent(SysConstants.SET_NX + expiredKey, String.valueOf(System.currentTimeMillis()), 3);
        if (b) {
            log.info("过期值：{}", expiredKey);
            try {
                orderService.handlePayingOrderExpired(expiredKey);
            } catch (Exception e) {
                log.error("订单过期处理失败，订单号：{}", expiredKey, e);
            }
        } else {
            log.info("别的服务处理过了，俺就不处理啦");
        }
    }
}
