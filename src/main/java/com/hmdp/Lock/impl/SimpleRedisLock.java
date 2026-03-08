package com.hmdp.Lock.impl;

import com.hmdp.Lock.ILock;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {

    private String name;
    private final static String KEY_PREFIX="lock:";
    private StringRedisTemplate stringRedisTemplate;
    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }


    @Override
    public boolean tryLock(long timeoutSec) {
        String key=KEY_PREFIX+name;
        long value = Thread.currentThread().getId();
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(key, value+"", timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        //释放锁
        stringRedisTemplate.delete(KEY_PREFIX+name);
    }
}
