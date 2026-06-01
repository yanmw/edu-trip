package com.cui.edu.config.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

/**
 * @author Cuicui
 * @version 1.0
 * @description: 消息消费：
 * 消息监听注册配置，把消息监听注册到容器里面
 */
@Configuration
public class RedisConsumeConfig {

    /**
     * 1、注入消息监听容器
     *
     * @param connectionFactory          连接工厂
     * (参数名称需和监听处理器的方法名称一致，因为@Bean注解默认注入的id就是方法名称)
     * @return
     */
    @Bean
    RedisMessageListenerContainer container(LettuceConnectionFactory connectionFactory,
                                            MessageListenerAdapter listenerAdapterUnionRefundQuery) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        //订阅一个叫mq_zzg_union_refund_query 的信道
        container.addMessageListener(listenerAdapterUnionRefundQuery, new PatternTopic("mq_union_refund_query"));
        return container;
    }
    /**
     * 消息监听处理器：银联退款查询
     *
     * @param receiver 处理器类
     * @return
     */
    @Bean
    MessageListenerAdapter listenerAdapterUnionRefundQuery(RedisMessageReceiverConfig receiver) {
        //给messageListenerAdapter 传入一个消息接收的处理器，利用反射的方法调用“receiveMessageUnionRefundQuery”,receiveMessageUnionRefundQuery：接收消息的方法名称
        return new MessageListenerAdapter(receiver, "receiveMessageUnionRefundQuery");
    }

}
