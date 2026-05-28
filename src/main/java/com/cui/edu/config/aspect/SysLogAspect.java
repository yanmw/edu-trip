package com.cui.edu.config.aspect;

import cn.hutool.core.stream.StreamUtil;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.stream.Collectors;

@Aspect
@Component
@Slf4j
public class SysLogAspect {

    /**
     * 基于方法执行的切点表达式，它匹配所有com.cui.zzg包及其子包中controller包下的所有方法
     */
    @Pointcut("execution(* com.cui.edu.*.controller..*.*(..))")
    public void logPointCut() {

    }

    @Before("logPointCut()")
    public void doBefore(JoinPoint joinPoint) {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            HttpServletRequest request = attributes.getRequest();
            String className = joinPoint.getTarget().getClass().getName();
            String methodName = joinPoint.getSignature().getName();
            Object[] parameters = joinPoint.getArgs();
            List<Object> logArgs = StreamUtil.of(parameters)
                    .filter(arg -> (!(arg instanceof HttpServletRequest) && !(arg instanceof HttpServletResponse)))
                    .collect(Collectors.toList());
            log.info("==================" + methodName + "接口请求日志开始==================");
            log.info("\n" + "URL:" + request.getRequestURL().toString() + "\n"
                    + "HTTP Method:" + request.getMethod() + "\n"
                    + "类名:" + className + "\n"
                    + "方法名:" + methodName + "\n"
                    + "请求参数:" + JSON.toJSONString(logArgs));
        } catch (Throwable e) {
            log.info("around " + joinPoint + " with exception : " + e.getMessage());
        }
    }

    @Around("logPointCut()")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        String className = point.getTarget().getClass().getName();
        String methodName = point.getSignature().getName();
        long beginTime = System.currentTimeMillis();
        // 执行方法
        Object result = point.proceed();
        // 执行时长(毫秒)
        long time = System.currentTimeMillis() - beginTime;
        try {
            log.info("\n" + "类名:" + className + "\n"
                    + "方法名:" + methodName + "\n"
                    + "返回结果:" + JSON.toJSONString(result) + "\n"
                    + "方法执行耗时:" + time + "ms"
            );
        } catch (Throwable e) {
            log.info("around " + point + " with exception : " + e.getMessage());
        }
        log.info("==================" + methodName + "接口返回日志结束==================");
        return result;
    }

}
