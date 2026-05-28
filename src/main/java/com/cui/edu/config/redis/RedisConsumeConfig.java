package com.cui.edu.config.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

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
    RedisMessageListenerContainer container(LettuceConnectionFactory connectionFactory) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }


}
