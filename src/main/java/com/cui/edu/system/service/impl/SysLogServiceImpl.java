package com.cui.edu.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cui.edu.common.PageResult;
import com.cui.edu.common.PageResultUtil;
import com.cui.edu.system.entity.SysLog;
import com.cui.edu.system.mapper.SysLogMapper;
import com.cui.edu.system.service.SysLogService;
import com.cui.edu.vo.system.SysLogVO;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 系统操作日志 服务实现类
 * </p>
 *
 * @author Cuicui
 * @since 2026-07-02
 */
@Service
public class SysLogServiceImpl extends ServiceImpl<SysLogMapper, SysLog> implements SysLogService {

    @Override
    public PageResult findPage(SysLogVO vo) {
        Page<SysLog> page = new Page<>(vo.getPageNum(), vo.getPageSize());
        QueryWrapper<SysLog> ew = new QueryWrapper<>();
        if (StringUtils.isNotBlank(vo.getUserName())) {
            ew.like(SysLog.USER_NAME, vo.getUserName());
        }
        if (StringUtils.isNotBlank(vo.getOperation())) {
            ew.like(SysLog.OPERATION, vo.getOperation());
        }
        ew.orderByDesc(SysLog.ID);
        page = super.page(page, ew);
        return PageResultUtil.getPageResult(page);
    }

    @Override
    public List<String> listUserNames() {
        return baseMapper.listUserNames();
    }

    @Override
    public List<String> listOperations() {
        return baseMapper.listOperations();
    }

}
