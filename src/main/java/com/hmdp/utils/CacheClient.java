package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import io.lettuce.core.resource.DefaultClientResources;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.sql.Time;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Component
@Slf4j
public class CacheClient {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public void set(String key, Object value, Long time, TimeUnit unit){


        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){
        RedisData redisData = RedisData.builder()
                .data(value)
                .expireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)))
                .build();

        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }

    public <R,ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type,
                                         Function<ID,R>dbFallBack, Long time, TimeUnit unit){

        String key=keyPrefix+id;
        String json = stringRedisTemplate.opsForValue().get(key);
        if(StrUtil.isNotBlank(json)){
             return JSONUtil.toBean(json, type);
        }
        if(json!=null){
            return null;
        }
        R  queryResult = dbFallBack.apply(id);
        if (queryResult == null) {
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        this.set(key,queryResult,time,unit);
        return queryResult;
    }

    public <R,ID>  R queryWithLogicalExpire(String keyPrefix, ID id,Class<R> type,Function<ID,R> dbFallBack,
                                            Long time, TimeUnit unit){

        String key=keyPrefix+id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //此处判断仅为了鲁棒性考虑，事实上在实际开发中会提前在缓存中存入会被高并发访问的key,因此查出来的缓存绝对不会为null
        if(StrUtil.isBlank(shopJson)){
            return null;
        }
        //存在
        RedisData bean = JSONUtil.toBean(shopJson, RedisData.class);
        // 判断数据是否有效
        if(bean.getData() == null || bean.getExpireTime() == null){
            return null;
        }

        JSONObject data = (JSONObject) bean.getData();
        R r = JSONUtil.toBean(data, type);
        //判断是否过期
        // 未过期 直接返回数据
        if(bean.getExpireTime().isAfter(LocalDateTime.now())){
            return r;
        }
        //过期 缓存重建
        boolean isLock = tryLock(LOCK_SHOP_KEY + id);
        if(isLock){
            //Double Check
            String Json = stringRedisTemplate.opsForValue().get(key);
            RedisData redisData = JSONUtil.toBean(Json, RedisData.class);
            if (redisData.getExpireTime() != null && redisData.getExpireTime().isAfter(LocalDateTime.now())) {
                JSONObject data1 = (JSONObject) redisData.getData();
                return  JSONUtil.toBean(data1, type);
            }

            //开线程，创建数据写入缓存
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    //查数据库
                    R  res = dbFallBack.apply(id);
                    Thread.sleep(20);
                    //写入缓存
                    this.setWithLogicalExpire(key,res,time,unit);

                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    unlock(LOCK_SHOP_KEY + id);
                }

            });
        }
        //返回旧数据
        return r;
    }

    private boolean tryLock(String key){

        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);

    }

    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }
}

