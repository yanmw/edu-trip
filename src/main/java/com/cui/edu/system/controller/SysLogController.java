package com.cui.edu.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.cui.edu.common.HttpResult;
import com.cui.edu.common.PageResult;
import com.cui.edu.system.service.SysLogService;
import com.cui.edu.vo.system.SysLogVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 系统操作日志 前端控制器
 * </p>
 *
 * @author Cuicui
 * @since 2026-07-02
 */
@RestController
@RequestMapping("/system/sys-log")
@Api(tags = "系统日志管理")
public class SysLogController {

    @Autowired
    private SysLogService sysLogService;

    /**
     * 分页查询系统操作日志
     *
     * @param vo 查询参数（用户名、操作类型、页码、页大小）
     * @return 分页数据
     */
    @PostMapping("/findPage")
    @ApiOperation("分页查询系统操作日志")
    @SaCheckPermission("sys:log:search")
    public HttpResult findPage(@RequestBody SysLogVO vo) {
        PageResult pageResult = sysLogService.findPage(vo);
        return HttpResult.ok(pageResult);
    }

    /**
     * 获取所有不重复的用户名（用于下拉选择）
     */
    @GetMapping("/listUserNames")
    @ApiOperation("获取用户名下拉列表")
    @SaCheckPermission("sys:log:search")
    public HttpResult listUserNames() {
        List<String> list = sysLogService.listUserNames();
        return HttpResult.ok(list);
    }

    /**
     * 获取所有不重复的用户操作（用于下拉选择）
     */
    @GetMapping("/listOperations")
    @ApiOperation("获取用户操作下拉列表")
    @SaCheckPermission("sys:log:search")
    public HttpResult listOperations() {
        List<String> list = sysLogService.listOperations();
        return HttpResult.ok(list);
    }

}
