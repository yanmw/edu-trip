package com.cui.edu.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cui.edu.common.PageResult;
import com.cui.edu.system.entity.SysLog;
import com.cui.edu.vo.system.SysLogVO;

import java.util.List;

/**
 * <p>
 * 系统操作日志 服务类
 * </p>
 *
 * @author Cuicui
 * @since 2026-07-02
 */
public interface SysLogService extends IService<SysLog> {

    PageResult findPage(SysLogVO vo);

    /**
     * 获取所有不重复的用户名（用于下拉选择）
     */
    List<String> listUserNames();

    /**
     * 获取所有不重复的用户操作（用于下拉选择）
     */
    List<String> listOperations();

}
