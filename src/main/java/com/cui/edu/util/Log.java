package com.cui.edu.util;

import java.lang.annotation.*;

/**
*@Description  自定义操作日志记录注解
*@author Cuicui
*/
@Target({ ElementType.PARAMETER, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Log
{
    /** 模块 */
    String title() default "";

}
