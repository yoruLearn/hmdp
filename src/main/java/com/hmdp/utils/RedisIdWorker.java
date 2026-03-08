package com.hmdp.utils;

import org.apache.tomcat.jni.Local;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static java.time.LocalTime.now;

@Component
public class RedisIdWorker {
    /*

    基于Redis的自增 全局唯一id的生成：
    构造格式：
    符号位（1位）+时间戳(31位)+ 序列号(32位) 共64位
     */
    private final static long BEGIN_TIMESTAMP=1772901143;

    private final static int COUNT_BITS=32;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /*
   @Param: 业务前缀
   @return: 全局唯一id
     */
    public long nextId(String prefix){

        long now = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
    //时间戳
        long timeStamp = now - BEGIN_TIMESTAMP;
    //生成序列号
        String date =LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        long count = stringRedisTemplate.opsForValue().increment("incr :" + timeStamp + date);

        return timeStamp<<COUNT_BITS| count;
    }



}
