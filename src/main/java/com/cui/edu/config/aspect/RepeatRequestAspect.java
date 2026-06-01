package com.cui.edu.config.aspect;


import com.cui.edu.common.HttpResult;
import com.cui.edu.common.HttpStatus;
import com.cui.edu.util.AvoidRepeatRequest;
import com.cui.edu.util.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;

@Component
@Aspect
@Slf4j
public class RepeatRequestAspect {
    private static final String SUFFIX = "REQUEST_";

    @Autowired
    private RedisUtils redisUtils;

    /**
     * 基于注解的切点表达式，它匹配所有带有com.cui.zzg.util.AvoidRepeatRequest注解的方法。
     */
    @Pointcut("@annotation(com.cui.edu.util.AvoidRepeatRequest)")
    public void arrPointcut() {
    }

    @Around("arrPointcut()")
    public Object arrBusiness(ProceedingJoinPoint joinPoint) {
        // 获取 redis key，由 session ID 和 请求URI 构成
        ServletRequestAttributes sra = (ServletRequestAttributes)
                RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = sra.getRequest();
        String key = SUFFIX + request.getSession().getId() + "_" + request.getRequestURI();

        // 获取方法的 AvoidRepeatRequest 注解
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        AvoidRepeatRequest arr = method.getAnnotation(AvoidRepeatRequest.class);

        // 判断是否是重复的请求
        if (!redisUtils.setIfAbsent(key, 1, arr.intervalTime())) {
            // 已发起过请求
            log.info("重复请求");
            HttpResult httpResult = new HttpResult();
            httpResult.setMsg(arr.msg());
            httpResult.setCode(HttpStatus.SC_MY_ERROR);
            return httpResult;
        }

        try {
            // 非重复请求，执行业务代码
            return joinPoint.proceed();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            HttpResult httpResult = new HttpResult();
            httpResult.setMsg("系统异常，请稍后再试");
            httpResult.setCode(HttpStatus.SC_MY_ERROR);
            return httpResult;
        }
    }
}
