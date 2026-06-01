package com.cui.edu.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.List;

/**
 * @author Cuicui
 * @description 跨域、拦截器配置
 **/
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${edu.file.upload-path:${user.dir}/upload}")
    private String uploadPath;

    @Value("${edu.file.request-prefix:/files}")
    private String requestPrefix;

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOriginPattern("*");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.setAllowCredentials(true);
        config.setMaxAge(168000L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        //允许跨域访问路径
        CorsRegistration corsRegistration = registry.addMapping("/**")
                //允许请求的方法
                .allowedMethods("POST", "GET", "PUT", "OPTIONS", "DELETE")
                //预间隔时间
                .maxAge(168000)
                //允许头部设置
                .allowedHeaders("*")
                //是否发送cookie
                .allowCredentials(true);
        corsRegistration.allowedOriginPatterns("*");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String prefix = normalizeRequestPrefix();
        String location = Paths.get(uploadPath).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler(prefix + "/**").addResourceLocations(location);
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.forEach(converter -> {
            if (converter instanceof StringHttpMessageConverter) {
                ((StringHttpMessageConverter) converter).setDefaultCharset(Charset.forName("UTF-8"));
            }
        });
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
