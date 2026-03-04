package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveGeoOperationsExtensionsKt;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号
        boolean phoneInvalid = RegexUtils.isPhoneInvalid(phone);

        if(phoneInvalid){
            return Result.fail("手机号不合法,请输入正确的手机号");
        }

        //生成验证码
        String  code = RandomUtil.randomNumbers(6);
        //将验证码存储到redis里 key:手机号  v:验证码
        redisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        log.info("验证码发送成功,验证码为:{}",code);
        //返回结果
        return Result.ok();
    }

    /*

    登录功能

    auth:lxl
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
     //校验验证码
        String code = loginForm.getCode();

        String myCode = redisTemplate.opsForValue().get(LOGIN_CODE_KEY+loginForm.getPhone());
        if(myCode==null ||!myCode.equals(code)){
            return Result.fail("验证码错误,请重新输入");
        }
        //验证成功
        //根据手机号查用户
        User user = query().eq("phone", loginForm.getPhone()).one();

        if(user==null){
             //创建用户并保存
            user= createUserWithPhone(loginForm.getPhone());
        }
        //将用户信息保存到redis里
        //TODO:随机生成token作为key
        String token = UUID.randomUUID().toString();
        //todo:将User变成hash类型并存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

        /*

        由于Long类型无法转换成string类型，于是传入参数CopyOptions.create().setFieldValueEditor
        使得值的类型变为我们想要的类型
         */
        Map<String, Object> map = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true)
                        .setFieldValueEditor((fn,fv)->fv.toString()));

        //todo:存储
        redisTemplate.opsForHash().putAll(LOGIN_USER_KEY+token,map);
        //设置有效期
        redisTemplate.expire(LOGIN_USER_KEY+token,LOGIN_USER_TTL,TimeUnit.SECONDS);
        return Result.ok(token);


    }

    private User createUserWithPhone(String phone) {
        //创建用户
        User user=new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
