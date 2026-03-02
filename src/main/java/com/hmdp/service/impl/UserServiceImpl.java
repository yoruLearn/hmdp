package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveGeoOperationsExtensionsKt;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

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
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号
        boolean phoneInvalid = RegexUtils.isPhoneInvalid(phone);

        if(!phoneInvalid){

            //生成验证码
            String  code = RandomUtil.randomNumbers(6);
            //保存到session
            session.setAttribute("code",code);
            log.info("验证码发送成功,验证码为:{}",code);
            //返回结果
            return Result.ok();
        }

        return Result.fail("手机号不合法,请输入正确的手机号");

    }

    /*

    登录功能

    auth:lxl
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
     //校验验证码
        String code = loginForm.getCode();

        Object myCode = session.getAttribute("code");
        if(myCode==null ||!myCode.toString().equals(code)){
            return Result.fail("验证码错误,请重新输入");
        }
        //验证成功
        //根据手机号查用户
        User user = query().eq("phone", loginForm.getPhone()).one();

        if(user==null){
             //创建用户并保存
            user= createUserWithPhone(loginForm.getPhone());
        }
        session.setAttribute("user",user);
        return Result.ok();


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
