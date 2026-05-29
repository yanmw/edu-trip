package com.cui.edu.config.db;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.cui.edu.util.DateTimeUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

/**
 * sql新增、修改自动装配 操作时间和操作人
 * 配合注解@TableField(fill = FieldFill.INSERT)、@TableField(fill = FieldFill.INSERT_UPDATE)完成
 * 不加注解不会注入
 */
@Slf4j
@Component
public class MyMetaObjectHandleConfig implements MetaObjectHandler {
    @Override
    public void insertFill(MetaObject metaObject) {
        log.info("进入创建");
        Object createTime = getFieldValByName("createTime", metaObject);
        if (createTime == null) {
            this.setFieldValByName("createTime", DateTimeUtils.getLocalDateTime(), metaObject);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        log.info("进入修改");
        Object updateTime = getFieldValByName("updateTime", metaObject);
        if (updateTime == null) {
            this.setFieldValByName("updateTime", DateTimeUtils.getLocalDateTime(), metaObject);
        }
    }

}
