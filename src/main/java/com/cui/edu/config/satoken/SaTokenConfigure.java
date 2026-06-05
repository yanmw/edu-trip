package com.cui.edu.config.satoken;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {

    @Value("${edu.file.request-prefix:/files}")
    private String requestPrefix;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        String fileRequestPattern = normalizeRequestPrefix() + "/**";
        // 单个 Sa-Token 拦截器即可同时支持注解鉴权和登录校验；接口放行统一使用 @SaIgnore。
        registry.addInterceptor(new SaInterceptor(handler -> StpUtil.checkLogin()))
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
