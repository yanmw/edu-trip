package com.cui.edu.system.controller;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cui.edu.common.HttpResult;
import com.cui.edu.common.HttpStatus;
import com.cui.edu.common.PageResult;
import com.cui.edu.common.SysConstants;
import com.cui.edu.system.entity.SysDept;
import com.cui.edu.system.service.SysDeptService;
import com.cui.edu.vo.system.DeptVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 部门管理 前端控制器
 * </p>
 *
 * @author Cuicui
 * @since 2026-02-25
 */
@RestController
@RequestMapping("/system/sys-dept")
@Api(tags = "部门管理")
public class SysDeptController {

    @Autowired
    private SysDeptService deptService;

    @PostMapping(value = "/save")
    @ApiOperation(value = "新增/修改部门")
    public HttpResult save(@RequestBody SysDept record) {
        if (BeanUtil.isNotEmpty(record)) {
            if (record.getId() == null && record.getStatus() == null) {
                record.setStatus(SysConstants.IS_TRUE);
            }
            deptService.saveOrUpdate(record);
            return HttpResult.ok(record.getId());
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    @PostMapping(value = "/disableFeature")
    @ApiOperation(value = "禁用部门")
    public HttpResult delete(@RequestBody List<Long> records) {
        if (ObjectUtil.isNotEmpty(records)) {
            deptService.logicDelete(records);
            return HttpResult.ok();
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    @PostMapping(value = "/findPage")
    @ApiOperation(value = "部门查询-分页")
    public HttpResult findPage(@RequestBody DeptVO vo) {
        if (BeanUtil.isNotEmpty(vo)) {
            PageResult pageResult = deptService.findPage(vo);
            return HttpResult.ok(pageResult);
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    @GetMapping(value = "/findAll")
    @ApiOperation(value = "查询全部部门")
    public HttpResult findAll() {
        QueryWrapper<SysDept> ew = new QueryWrapper<>();
        ew.eq(SysDept.STATUS, SysConstants.IS_TRUE);
        ew.orderByDesc(SysDept.ID);
        List<SysDept> deptList = deptService.list(ew);
        return HttpResult.ok(deptList);
    }
}
