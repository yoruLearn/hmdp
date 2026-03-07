package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;
    /*

    使用缓存空对象解决缓存穿透问题,减小数据库压力
     */

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Override
    public Result queryById(Long id) {
//        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class,
//                this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        return shop==null?Result.fail("商铺不存在"): Result.ok(shop);

        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById,
                CACHE_SHOP_TTL, TimeUnit.SECONDS);
        return shop==null?Result.fail("商铺不存在"): Result.ok(shop);
    }
//    public Shop queryWithLogicalExpire(Long id){
//
//        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//        if(StrUtil.isBlank(shopJson)){
//            return null;
//        }
//        //存在
//        RedisData bean = JSONUtil.toBean(shopJson, RedisData.class);
//        // 判断数据是否有效
//        if(bean.getData() == null || bean.getExpireTime() == null){
//            return null;
//        }
//        JSONObject data = (JSONObject) bean.getData();
//        Shop shop = JSONUtil.toBean(data, Shop.class);
//        //判断是否过期
//        // 未过期 直接返回数据
//        if(bean.getExpireTime().isAfter(LocalDateTime.now())){
//            return shop;
//        }
//        //过期 缓存重建
//        boolean isLock = tryLock(LOCK_SHOP_KEY + id);
//        if(isLock){
//            //Double Check
//            String Json = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//            RedisData redisData = JSONUtil.toBean(Json, RedisData.class);
//            if (redisData.getExpireTime() != null && redisData.getExpireTime().isAfter(LocalDateTime.now())) {
//                JSONObject data1 = (JSONObject) redisData.getData();
//                return  JSONUtil.toBean(data1, Shop.class);
//            }
//            //开线程，创建数据写入缓存
//            CACHE_REBUILD_EXECUTOR.submit(()->{
//                try {
//                    saveShop2Redis(id,20L);
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                }finally {
//                    unlock(LOCK_SHOP_KEY + id);
//                }
//
//            });
//            Shop queryResult = query().eq("id", id).one();
//
//            String jsonStr = JSONUtil.toJsonStr(queryResult);
//            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,jsonStr,CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        }
//        //返回旧数据
//        return shop;
//
//
//
//
//    }

//    public Shop queryWithPassThrough(Long id){
//
//        String shop = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//        if(StrUtil.isNotBlank(shop)){
//            Shop res = JSONUtil.toBean(shop, Shop.class);
//            return res;
//        }
//        if(shop!=null){
//            return null;
//        }
//        Shop queryResult = query().eq("id", id).one();
//        if (queryResult == null) {
//
//            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
//            return null;
//        }
//        String jsonStr = JSONUtil.toJsonStr(queryResult);
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,jsonStr,CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        return queryResult;
//    }


    /*
    更新商铺 先更新数据库，再删除缓存
     */
    @Override
    @Transactional
    public Result updateShop(Shop shop) {
       updateById(shop);
       Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        stringRedisTemplate.delete(CACHE_SHOP_KEY+id);
        return Result.ok();
    }
}

