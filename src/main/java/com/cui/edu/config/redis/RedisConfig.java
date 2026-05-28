package com.cui.edu.config.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;


/**
 * redis配置类
 * @author Cuicui
 *
 */

@Configuration
public class RedisConfig {

    /**
     * springboot2.x 使用LettuceConnectionFactory 代替 RedisConnectionFactory
     * application.yml配置基本信息后,springboot2.x  RedisAutoConfiguration能够自动装配
     * LettuceConnectionFactory 和 RedisConnectionFactory 及其 RedisTemplate
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(LettuceConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        // GenericJackson2JsonRedisSerializer是一种更加通用的序列化方式，它使用Jackson库将对象序列化为JSON格式的字符串，然后存储到Redis中。
        // 这种方式的优点是可以序列化任何类型的数据，包括复杂的对象和集合类型。同时，JSON格式的数据也更加紧凑，可以节省存储空间。
        redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        // 设置连接池
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        //事务支持--关闭
        redisTemplate.setEnableTransactionSupport(false);
        return redisTemplate;
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory redisConnectionFactory) {
        StringRedisTemplate redisTemplate = new StringRedisTemplate();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        //事务支持开关，和spring的事务共同作用，导致在加有@Transactional注解的方法上，redis无法正常读取数据，只能关闭。后续如果需要redis事务，另外注入一个bean即可
        redisTemplate.setEnableTransactionSupport(false);
        return redisTemplate;
    }
}