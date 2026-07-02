package com.cui.edu.config.aspect;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.stream.StreamUtil;
import com.alibaba.fastjson2.JSON;
import com.cui.edu.system.entity.SysLog;
import com.cui.edu.system.service.SysLogService;
import com.cui.edu.util.Log;
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
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 操作日志切面
 * <p>
 * 切点：所有 controller 包下的方法
 * - 所有方法：打印控制台请求/响应日志（原有行为保留）
 * - 同时标注了 @Log 的方法：额外将操作信息持久化到 sys_log 表
 * </p>
 *
 * @author Cuicui
 */
@Aspect
@Component
@Slf4j
public class SysLogAspect {

    @Autowired
    private SysLogService sysLogService;

    /**
     * 切点：匹配所有 controller 包下的方法
     */
    @Pointcut("execution(* com.cui.edu.*.controller..*.*(..))")
    public void controllerPointCut() {
    }

    /**
     * 统一拦截：
     * 1. 所有 controller 方法 → 打印控制台日志
     * 2. 加了 @Log 注解的方法 → 额外入库
     */
    @Around("controllerPointCut()")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        String className = point.getTarget().getClass().getName();
        String methodName = point.getSignature().getName();

        // 过滤掉 Request/Response/MultipartFile 对象后序列化参数
        List<Object> logArgs = StreamUtil.of(point.getArgs())
                .filter(arg -> arg != null
                        && !(arg instanceof HttpServletRequest)
                        && !(arg instanceof HttpServletResponse)
                        && !(arg instanceof MultipartFile))
                .collect(Collectors.toList());

        // 如果参数里有 MultipartFile，单独记录文件名和大小
        List<String> fileInfos = new ArrayList<>();
        for (Object arg : point.getArgs()) {
            if (arg instanceof MultipartFile) {
                MultipartFile file = (MultipartFile) arg;
                fileInfos.add("[文件:" + file.getOriginalFilename() + ", 大小:" + file.getSize() + "字节]");
            }
        }

        // ---- ① 打印请求控制台日志（原有行为） ----
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String paramsStr = JSON.toJSONString(logArgs)
                        + (fileInfos.isEmpty() ? "" : " 文件:" + fileInfos);
                log.info("=================={}接口请求日志开始==================", methodName);
                log.info("\nURL: {}\nHTTP Method: {}\n类名: {}\n方法名: {}\n请求参数: {}",
                        request.getRequestURL(), request.getMethod(),
                        className, methodName, paramsStr);
            }
        } catch (Exception e) {
            log.warn("请求日志打印失败: {}", e.getMessage());
        }

        // ---- 执行目标方法 ----
        long beginTime = System.currentTimeMillis();
        Object result = point.proceed();
        long time = System.currentTimeMillis() - beginTime;

        // ---- ② 打印响应控制台日志（原有行为） ----
        try {
            log.info("\n类名: {}\n方法名: {}\n返回结果: {}\n方法执行耗时: {}ms",
                    className, methodName, JSON.toJSONString(result), time);
        } catch (Exception e) {
            log.warn("响应日志打印失败: {}", e.getMessage());
        }
        log.info("=================={}接口返回日志结束==================", methodName);

        // ---- ③ 如果方法加了 @Log 注解，则额外入库 ----
        Method method = ((MethodSignature) point.getSignature()).getMethod();
        Log logAnnotation = method.getAnnotation(Log.class);
        if (logAnnotation != null) {
            try {
                saveLog(point, method, logAnnotation, logArgs, fileInfos, result, time);
            } catch (Exception e) {
                log.error("操作日志入库失败", e);
            }
        }

        return result;
    }

    /**
     * 构建并保存操作日志到数据库
     */
    private void saveLog(ProceedingJoinPoint point, Method method,
                         Log logAnnotation, List<Object> logArgs,
                         List<String> fileInfos,
                         Object result, long time) {
        MethodSignature signature = (MethodSignature) point.getSignature();

        // 获取请求 IP
        String ip = "";
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                ip = getClientIp(attributes.getRequest());
            }
        } catch (Exception ignored) {
        }

        // 获取当前登录用户（Sa-Token）
        String userName = "";
        try {
            if (StpUtil.isLogin()) {
                Object loginId = StpUtil.getLoginId();
                userName = loginId != null ? loginId.toString() : "";
            }
        } catch (Exception ignored) {
        }

        String params = JSON.toJSONString(logArgs)
                + (fileInfos.isEmpty() ? "" : " 文件:" + fileInfos);
        String returnParams = "";
        try {
            returnParams = JSON.toJSONString(result);
        } catch (Exception ignored) {
        }

        String fullMethodName = point.getTarget().getClass().getName()
                + "." + signature.getName() + "()";

        SysLog sysLog = new SysLog();
        sysLog.setOperation(logAnnotation.title());
        sysLog.setMethod(fullMethodName);
        sysLog.setParams(params);
        sysLog.setReturnParams(returnParams);
        sysLog.setTime(time);
        sysLog.setIp(ip);
        sysLog.setUserName(userName);
        sysLog.setCreateBy(userName);
        sysLog.setCreateTime(LocalDateTime.now());

        sysLogService.save(sysLog);

        log.info("[操作日志入库] 模块={} | 方法={} | 耗时={}ms | IP={} | 用户={}",
                logAnnotation.title(), fullMethodName, time, ip, userName);
    }

    /**
     * 获取客户端真实 IP（支持反向代理）
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (isBlank(ip)) ip = request.getHeader("Proxy-Client-IP");
        if (isBlank(ip)) ip = request.getHeader("WL-Proxy-Client-IP");
        if (isBlank(ip)) ip = request.getHeader("HTTP_CLIENT_IP");
        if (isBlank(ip)) ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        if (isBlank(ip)) ip = request.getRemoteAddr();
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private boolean isBlank(String str) {
        return str == null || str.isEmpty() || "unknown".equalsIgnoreCase(str);
    }
}
