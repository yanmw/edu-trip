package com.cui.edu.config.redis;

import com.cui.edu.common.SysConstants;
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
        // 获取过期的key,可以做自己的业务
        String expiredKey = message.toString();
        if (expiredKey.contains(SysConstants.SET_NX)) {
            log.info("锁，不再处理");
            return;
        }
        // 利用redis setIfAbsent命令,如果为空set返回true,如果不为空返回false,类似setnx加锁操作
        Boolean b = redisUtils.setIfAbsent(SysConstants.SET_NX + expiredKey, String.valueOf(System.currentTimeMillis()), 3);
        if (b) {
            log.info("过期值：{}", expiredKey);
        } else {
            log.info("别的服务处理过了，俺就不处理啦");
        }
    }
}
