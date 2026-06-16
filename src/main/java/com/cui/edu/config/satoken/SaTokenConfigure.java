package com.cui.edu.config.satoken;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.exception.SaTokenException;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.strategy.SaAnnotationStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;

@Configuration
@Slf4j
public class SaTokenConfigure implements WebMvcConfigurer {

    @Value("${edu.file.request-prefix:/files}")
    private String requestPrefix;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        String fileRequestPattern = normalizeRequestPrefix() + "/**";
        // 单个 Sa-Token 拦截器即可同时支持注解鉴权和登录校验；接口放行统一使用 @SaIgnore。
        registry.addInterceptor(new SaInterceptor(handler -> {
                    if (isSaIgnore(handler)) {
                        return;
                    }
                    try {
                        StpUtil.checkLogin();
                    } catch (SaTokenException e) {
                        log.warn("Sa-Token鉴权失败，请求接口：{}，处理方法：{}，原因：{}",
                                buildRequestDescription(), buildHandlerDescription(handler), e.getMessage(), e);
                        throw e;
                    }
                }))
                .addPathPatterns("/**")
                .excludePathPatterns("/error")
                .excludePathPatterns("/swagger-ui.html")
                .excludePathPatterns("/doc.html")
                .excludePathPatterns("/v2/api-docs")
                .excludePathPatterns("/swagger-resources")
                .excludePathPatterns("/swagger-resources/**")
                .excludePathPatterns("/webjars/**")
                .excludePathPatterns("/favicon.ico")
                .excludePathPatterns(fileRequestPattern);
    }

    private boolean isSaIgnore(Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        Method method = ((HandlerMethod) handler).getMethod();
        return SaAnnotationStrategy.instance.isAnnotationPresent.apply(method, SaIgnore.class);
    }

    private String buildRequestDescription() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "未知请求";
        }
        HttpServletRequest request = attributes.getRequest();
        String queryString = request.getQueryString();
        return request.getMethod() + " " + request.getRequestURI()
                + (StringUtils.hasText(queryString) ? "?" + queryString : "");
    }

    private String buildHandlerDescription(Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return String.valueOf(handler);
        }
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        return handlerMethod.getBeanType().getName() + "#" + handlerMethod.getMethod().getName();
    }

    private String normalizeRequestPrefix() {
        if (!StringUtils.hasText(requestPrefix)) {
            return "/files";
        }
        String prefix = requestPrefix.trim();
        if (!prefix.startsWith("/")) {
            prefix = "/" + prefix;
        }
        if (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix;
    }
}
