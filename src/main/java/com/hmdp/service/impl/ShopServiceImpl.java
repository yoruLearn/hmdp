package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    /*

    使用缓存空对象解决缓存穿透问题,减小数据库压力
     */

    @Override
    public Result queryById(Long id) {
        //缓存击穿解决
       // Shop shop = queryWithPassThrough(id);
        //用互斥锁解决缓存击穿
        Shop shop = queryWithPassMutex(id);
        return Result.ok(shop);

    }

    public Shop queryWithPassMutex(Long id){

        String shop = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        if(StrUtil.isNotBlank(shop)){
            Shop res = JSONUtil.toBean(shop, Shop.class);
            return res;
        }
        if(shop==null){
            return null;
        }
        // 1 尝试获取互斥锁
        String lockKey="lock_shop:"+id;
        boolean isLock = tryLock(lockKey);

        //2.判断是否获取
        Shop queryResult = null;
        try {
            if(!isLock){
                //失败：休眠一段时间，重试
                Thread.sleep(50);
                queryWithPassMutex(id);
            }
            //释放互斥锁
            //成功：根据id查询数据库
            queryResult = query().eq("id", id).one();
            //将数据写入redis
            if (queryResult == null) {
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,null,CACHE_NULL_TTL,TimeUnit.MINUTES);
                return null;
            }
            String jsonStr = JSONUtil.toJsonStr(queryResult);
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,jsonStr,CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unlock(lockKey);
        }


        return queryResult;
    }

    public Shop queryWithPassThrough(Long id){

        String shop = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        if(StrUtil.isNotBlank(shop)){
            Shop res = JSONUtil.toBean(shop, Shop.class);
            return res;
        }
        if(shop==null){

            return null;
        }
        Shop queryResult = query().eq("id", id).one();
        if (queryResult == null) {

            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,null,CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        String jsonStr = JSONUtil.toJsonStr(queryResult);
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,jsonStr,CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return queryResult;
    }

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

    private boolean tryLock(String key){

        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);

    }

    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }
}
