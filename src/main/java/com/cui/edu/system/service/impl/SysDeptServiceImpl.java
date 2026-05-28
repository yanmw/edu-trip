package com.cui.edu.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cui.edu.common.PageResult;
import com.cui.edu.common.PageResultUtil;
import com.cui.edu.common.SysConstants;
import com.cui.edu.system.entity.SysDept;
import com.cui.edu.system.mapper.SysDeptMapper;
import com.cui.edu.system.service.SysDeptService;
import com.cui.edu.vo.system.DeptVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author Cuicui
 * @since 2026-02-25
 */
@Service
public class SysDeptServiceImpl extends ServiceImpl<SysDeptMapper, SysDept> implements SysDeptService {

    @Override
    public PageResult findPage(DeptVO vo) {
        Page<SysDept> page = new Page<>(vo.getPageNum(), vo.getPageSize());
        QueryWrapper<SysDept> ew = new QueryWrapper<>();
        if (StringUtils.isNotBlank(vo.getName())) {
            ew.like(SysDept.NAME, vo.getName());
        }
        ew.eq(SysDept.STATUS, SysConstants.IS_TRUE);
        ew.orderByDesc(SysDept.ID);
        page = super.page(page, ew);
        return PageResultUtil.getPageResult(page);
    }

    @Override
    public void logicDelete(List<Long> ids) {
        List<SysDept> deptList = new ArrayList<>();
        for (Long id : ids) {
            SysDept dept = new SysDept();
            dept.setId(id);
            dept.setStatus(SysConstants.IS_FALSE);
            deptList.add(dept);
        }
        super.updateBatchById(deptList);
    }
}
