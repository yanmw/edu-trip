package com.cui.edu.config.db;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.PaginationInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Mybatis plus 配置
 * @author Cuicui
 */
@Configuration
@MapperScan({"com.cui.edu.*.mapper"})
public class MyBatisPlusConfig {
    /**
    *@Description 分页
    */
    @Bean
    public PaginationInterceptor paginationInterceptor() {
        return new PaginationInterceptor().setDbType(DbType.MYSQL);
    }
}
