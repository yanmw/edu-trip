package com.cui.edu.config.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁
 */
@Component
public class DistributedLockHandler {

    private static final Logger logger = LoggerFactory.getLogger(DistributedLockHandler.class);
    private final static long LOCK_EXPIRE = 30 * 1000L;//单个业务持有锁的时间30s，防止死锁
    private final static long LOCK_TRY_INTERVAL = 30L;//默认30ms尝试一次
    private final static long LOCK_TRY_TIMEOUT = 20 * 1000L;//默认尝试20s

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 尝试获取全局锁
     *
     * @param lock 锁的名称
     * @return true 获取成功，false获取失败
     */
    public boolean tryLock(Lock lock) {
        return getLock(lock, LOCK_TRY_TIMEOUT, LOCK_TRY_INTERVAL, LOCK_EXPIRE);
    }

    /**
     * 尝试获取全局锁
     *
     * @param lock    锁的名称
     * @param timeout 获取超时时间 单位ms
     * @return true 获取成功，false获取失败
     */
    public boolean tryLock(Lock lock, long timeout) {
        return getLock(lock, timeout, LOCK_TRY_INTERVAL, LOCK_EXPIRE);
    }

    /**
     * 尝试获取全局锁
     *
     * @param lock        锁的名称
     * @param timeout     获取锁的超时时间
     * @param tryInterval 多少毫秒尝试获取一次
     * @return true 获取成功，false获取失败
     */
    public boolean tryLock(Lock lock, long timeout, long tryInterval) {
        return getLock(lock, timeout, tryInterval, LOCK_EXPIRE);
    }

    /**
     * 尝试获取全局锁
     *
     * @param lock           锁的名称
     * @param timeout        获取锁的超时时间
     * @param tryInterval    多少毫秒尝试获取一次
     * @param lockExpireTime 锁的过期
     * @return true 获取成功，false获取失败
     */
    public boolean tryLock(Lock lock, long timeout, long tryInterval, long lockExpireTime) {
        return getLock(lock, timeout, tryInterval, lockExpireTime);
    }


    /**
     * 操作redis获取全局锁
     *
     * @param lock           锁的名称
     * @param timeout        获取的超时时间
     * @param tryInterval    多少ms尝试一次
     * @param lockExpireTime 获取成功后锁的过期时间
     * @return true 获取成功，false获取失败
     */
    public boolean getLock(Lock lock, long timeout, long tryInterval, long lockExpireTime) {
        boolean lockAcquired = false;
        try {
            if (ObjectUtils.isEmpty(lock.getName()) || ObjectUtils.isEmpty(lock.getValue())) {
                return false;
            }
            long startTime = System.currentTimeMillis();
            do {
                // 使用 SETNX 实现原子性
                Boolean result = redisTemplate.opsForValue().setIfAbsent(
                        lock.getName(),
                        lock.getValue(),
                        lockExpireTime,
                        TimeUnit.MILLISECONDS
                );
                if (Boolean.TRUE.equals(result)) {
                    // 获取锁成功
                    lockAcquired = true;
                    break;
                } else {
                    logger.debug("Lock is already held by another process.");
                }
                if (System.currentTimeMillis() - startTime > timeout) {
                    // 超过尝试时限，直接退出
                    break;
                }
                Thread.sleep(tryInterval);
            } while (!lockAcquired);

            return lockAcquired;
        } catch (InterruptedException e) {
            logger.error("Thread interrupted: " + e.getMessage());
            Thread.currentThread().interrupt(); // 恢复线程中断状态
            return false;
        }
    }

    /**
     * 释放锁
     */
    public boolean releaseLock(Lock lock) {
        if (ObjectUtils.isEmpty(lock.getName()) || ObjectUtils.isEmpty(lock.getValue())) {
            return false;
        }
        try {
            // 使用 Lua 脚本保证原子性
            String script = "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "return redis.call('del', KEYS[1]) " +
                    "else return 0 end";
            Long result = redisTemplate.execute(
                    new DefaultRedisScript<>(script, Long.class),
                    Collections.singletonList(lock.getName()),
                    lock.getValue()
            );
            return result != null && result > 0;
        } catch (Exception e) {
            logger.error("Error releasing lock: " + e.getMessage());
            return false;
        }
    }

}
