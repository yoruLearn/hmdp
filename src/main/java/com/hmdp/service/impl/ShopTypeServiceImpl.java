package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_LIST;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    /*
    给店铺类型查询加入缓存
    auth:lxl
     */
    @Override
    public List<ShopType> queryTypeList() {
        String queryResult = stringRedisTemplate.opsForValue().get(CACHE_SHOP_TYPE_LIST);
        if(StrUtil.isNotBlank(queryResult)){
            return JSONUtil.toList(queryResult, ShopType.class);
        }
        List<ShopType> list = query().orderByAsc("sort").list();
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_TYPE_LIST,JSONUtil.toJsonStr(list));
        return list;

    }
}
