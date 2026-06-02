package com.cui.edu.config.db;

import cn.dev33.satoken.stp.StpUtil;
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
        fillOperatorIfNull("createBy", metaObject);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        log.info("进入修改");
        Object updateTime = getFieldValByName("updateTime", metaObject);
        if (updateTime == null) {
            this.setFieldValByName("updateTime", DateTimeUtils.getLocalDateTime(), metaObject);
        }
        fillOperatorIfNull("updateBy", metaObject);
    }

    private void fillOperatorIfNull(String fieldName, MetaObject metaObject) {
        if (!metaObject.hasSetter(fieldName) || getFieldValByName(fieldName, metaObject) != null) {
            return;
        }
        Object operator = getCurrentOperator(metaObject.getSetterType(fieldName));
        if (operator != null) {
            this.setFieldValByName(fieldName, operator, metaObject);
        }
    }

    private Object getCurrentOperator(Class<?> fieldType) {
        try {
            if (!StpUtil.isLogin()) {
                return null;
            }
            Object loginId = StpUtil.getLoginIdDefaultNull();
            if (String.class.equals(fieldType)) {
                return loginId == null ? null : String.valueOf(loginId);
            }
            if (Long.class.equals(fieldType)) {
                Object userId = StpUtil.getSession().get("userId");
                if (userId instanceof Long) {
                    return userId;
                }
                return userId == null ? null : Long.valueOf(String.valueOf(userId));
            }
        } catch (Exception e) {
            log.warn("自动填充操作人失败", e);
        }
        return null;
    }

}
