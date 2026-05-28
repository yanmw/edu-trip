package com.cui.edu.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;




@Configuration
@EnableScheduling
@Slf4j
public class ScheduledConfig {

    @Value("${scheduled.enable}")
    private boolean enable;

    /**
     * 每天23点执行一次
     */
    @Scheduled(cron = "0 0 23 * * ?")
    public void orderTask() {
        if (!enable) {
            // 测试环境，不执行定时任务
            return;
        }
        try {
            log.info("呀吼～执行定时任务了");
        } catch (Exception e) {
            log.error("订单到期任务执行失败", e);
        }
    }

}
