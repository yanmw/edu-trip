package com.cui.edu.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cui.edu.system.entity.SysLog;

import java.util.List;

/**
 * <p>
 * 系统操作日志 Mapper 接口
 * </p>
 *
 * @author Cuicui
 * @since 2026-07-02
 */
public interface SysLogMapper extends BaseMapper<SysLog> {

    /**
     * 查询所有不重复的用户名（用于下拉选择）
     */
    List<String> listUserNames();

    /**
     * 查询所有不重复的用户操作（用于下拉选择）
     */
    List<String> listOperations();

}
