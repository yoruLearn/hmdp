package com.hmdp.utils;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.HashSet;
import java.util.Set;


@SpringBootTest
class RedisIdWorkerTest {

    @Resource
    private RedisIdWorker redisIdWorker;
}