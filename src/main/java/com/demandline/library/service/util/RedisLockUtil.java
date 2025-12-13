package com.demandline.library.service.util;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Utility class for distributed locking using Redis
 * Prevents race conditions in concurrent operations
 */
@Component
public class RedisLockUtil {
    private static final String LOCK_PREFIX = "loan:lock:";
    private static final long DEFAULT_LOCK_TIMEOUT_SECONDS = 30;
    
    private final RedisTemplate<String, String> redisTemplate;

    public RedisLockUtil(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Attempt to acquire a distributed lock
     * @param lockKey the key to lock
     * @param lockValue unique value to identify the lock holder
     * @return true if lock was acquired, false otherwise
     */
    public boolean acquireLock(String lockKey, String lockValue) {
        return Boolean.TRUE.equals(
            redisTemplate.opsForValue().setIfAbsent(
                LOCK_PREFIX + lockKey,
                lockValue,
                DEFAULT_LOCK_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
            )
        );
    }

    /**
     * Release a distributed lock
     * @param lockKey the key to unlock
     * @param lockValue the unique value that acquired the lock
     */
    public void releaseLock(String lockKey, String lockValue) {
        String key = LOCK_PREFIX + lockKey;
        // Only delete if the value matches (to prevent deleting other holders' locks)
        if (lockValue.equals(redisTemplate.opsForValue().get(key))) {
            redisTemplate.delete(key);
        }
    }

    /**
     * Wait for a lock to be released (polling)
     * @param lockKey the key to wait for
     * @param maxWaitTimeSeconds maximum time to wait
     * @return true if lock was released, false if timeout
     */
    public boolean waitForLock(String lockKey, long maxWaitTimeSeconds) {
        long startTime = System.currentTimeMillis();
        long timeoutMs = maxWaitTimeSeconds * 1000;
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (!redisTemplate.hasKey(LOCK_PREFIX + lockKey)) {
                return true;
            }
            try {
                Thread.sleep(100); // Poll every 100ms
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /**
     * Check if a lock exists
     * @param lockKey the key to check
     * @return true if lock exists
     */
    public boolean lockExists(String lockKey) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(LOCK_PREFIX + lockKey));
    }
}

