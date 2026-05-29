package com.cui.edu.util;

import com.cui.edu.common.HttpStatus;
import com.cui.edu.config.exception.MyException;
import com.cui.edu.config.redis.DistributedLockHandler;
import com.cui.edu.config.redis.Lock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 订单流水号生成规则
 *
 * @author Cuicui
 */
@Component
@Slf4j
public class TextCodeGenerator {
    @Autowired
    private DistributedLockHandler distributedLockHandler;

    @Value("${code.prefix}")
    private String prefix;

    public String generate() {
        // 通过分布式事务锁的方式获取唯一单号
        Lock lock = new Lock("createOrderId", "lock.");
        boolean b = distributedLockHandler.tryLock(lock);
        if (b) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyMMddHHmmssSSS");
            // 只保留年份的最后一位 例如：40830154652513
            String formattedDateTime = LocalDateTime.now().format(formatter).substring(1);
            // 前缀 + 时间
            String result = prefix + formattedDateTime;
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            distributedLockHandler.releaseLock(lock);
            return result;
        } else {
            log.info("获取锁失败");
            distributedLockHandler.releaseLock(lock);
            throw new MyException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "获取锁失败");
        }
    }
}
