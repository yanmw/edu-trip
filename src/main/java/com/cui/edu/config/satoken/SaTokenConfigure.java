package com.cui.edu.config.satoken;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
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
        // 注册 Sa-Token 拦截器，定义详细认证规则
        registry.addInterceptor(new SaInterceptor(handler -> {
            String fileRequestPattern = normalizeRequestPrefix() + "/**";
            // 指定一条 match 规则
            SaRouter
                    .match("/**")    // 拦截的 path 列表，可以写多个 */
                    .notMatch("/system/sys-user/doLogin")        // 排除掉的 path 列表，可以写多个---也可以使用@SaIgnore 注解
                    .notMatch("/swagger-ui.html")
                    .notMatch("/doc.html")
                    .notMatch("/v2/api-docs")
                    .notMatch("/swagger-resources")
                    .notMatch("/swagger-resources/**")
                    .notMatch("/webjars/**")
                    .notMatch("/favicon.ico")
                    .notMatch(fileRequestPattern)
                    .check(r -> StpUtil.checkLogin());        // 要执行的校验动作，可以写完整的 lambda 表达式

        })).addPathPatterns("/**")
                .excludePathPatterns("/error");
        // 注册 Sa-Token 拦截器，打开注解式鉴权功能
        registry.addInterceptor(new SaInterceptor()).addPathPatterns("/**")
                .excludePathPatterns("/error");
        //我们可以使用 @SaIgnore 注解，忽略掉路由拦截认证
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
