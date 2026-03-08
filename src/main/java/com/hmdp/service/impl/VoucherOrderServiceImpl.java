package com.hmdp.service.impl;

import com.hmdp.Lock.ILock;
import com.hmdp.Lock.impl.SimpleRedisLock;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private SeckillVoucherServiceImpl seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    /*

    解决超卖问题：使用乐观锁解决并发安全问题

    解决一人一单问题

     */
    @Override
    public Result seckillVoucher(Long voucherId) {

        //查询优惠券
        SeckillVoucher seckillVoucher =seckillVoucherService.getById(voucherId);
        //判断秒杀是否开始
        LocalDateTime beginTime = seckillVoucher.getBeginTime();
        LocalDateTime endTime = seckillVoucher.getEndTime();
        if(beginTime.isAfter(LocalDateTime.now())){
            return Result.fail("秒杀活动尚未开始");
        }
        if(endTime.isBefore(LocalDateTime.now())){
            return Result.fail("秒杀活动已经结束");
        }

        Integer stock = seckillVoucher.getStock();
        //判断库存是否充足
        if(stock<=0){
            return Result.fail("库存不足,无法下单");

        }
        Long id = UserHolder.getUser().getId();

         ILock lock=new SimpleRedisLock(stringRedisTemplate,"order:"+id);
        if (!lock.tryLock(1200)) {
            return Result.fail("一人只允许下一单");
        }
            //获取代理对象
        IVoucherOrderService proxy = null;
        try {
            proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } catch (IllegalStateException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }
    @Transactional
    public  Result createVoucherOrder(Long voucherId){
            Long id = UserHolder.getUser().getId();

            int count = query().eq("user_id", id).eq("voucher_id", voucherId).count();
            if(count>0){
                return Result.fail("用户已经下过一单了");
            }

            //扣减库存
            //乐观锁，用库存代替版本号
            boolean success = seckillVoucherService.update()
                    .setSql("stock=stock-1")
                    .eq("voucher_id", voucherId).gt("stock",0)
                    .update();
            //创建订单
            if(!success){
                return Result.fail("库存不足");
            }

            VoucherOrder order = VoucherOrder.builder().userId(UserHolder.getUser().getId())
                    .id(redisIdWorker.nextId("order"))
                    .voucherId(voucherId)
                    .createTime(LocalDateTime.now())
                    .build();

            save(order);
            return Result.ok(order);
    }

}
