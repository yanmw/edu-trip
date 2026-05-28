package com.cui.edu.config;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ScheduledExecutorFactoryBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程池配置
 * @author Cuicui
 */
@Configuration
@EnableAsync
public class ThreadPoolConfig implements AsyncConfigurer {

    @Value("${spring.task.execution.pool.core-size}")
    private int corePoolSize;

    @Value("${spring.task.execution.pool.max-size}")
    private int maxPoolSize;

    @Value("${spring.task.execution.pool.queue-capacity}")
    private int queueCapacity;

    @Override
    @Bean("asyncExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("PlatformThread-");
        /**
         * 线程池的四种拒绝策略，我们采用第二种，保证线程里的数据都能执行成功
         * 1、AbortPolicy（默认）：抛出RejectedExecutionException异常，表示任务无法被执行。
         *
         * 2、CallerRunsPolicy：将任务交给调用线程来执行，即在调用线程中直接执行该任务。
         *
         * 3、DiscardPolicy：直接丢弃任务，不做任何处理。
         *
         * 4、DiscardOldestPolicy：丢弃队列中最旧的任务，然后尝试重新提交当前任务。
         */
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new SimpleAsyncUncaughtExceptionHandler();
    }

    /**
     * 定时任务线程池
     * @return
     */
    @Bean
    public ScheduledExecutorService scheduledExecutorService() {
        ScheduledExecutorFactoryBean factoryBean = new ScheduledExecutorFactoryBean();
        // 设置核心线程数
        factoryBean.setPoolSize(1);
        factoryBean.setThreadNamePrefix("PlatformScheduledThread-");
        // 手动调用以初始化
        factoryBean.afterPropertiesSet();
        return factoryBean.getObject();
    }
}
