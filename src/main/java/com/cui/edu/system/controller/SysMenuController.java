package com.cui.edu.system.controller;


import cn.dev33.satoken.annotation.SaMode;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import com.cui.edu.common.HttpResult;
import com.cui.edu.common.HttpStatus;
import com.cui.edu.system.entity.SysMenu;
import com.cui.edu.system.service.SysMenuService;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.cui.edu.system.service.SysRoleMenuService;
import com.cui.edu.util.Log;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 菜单管理 前端控制器
 * </p>
 *
 * @author Cuicui
 * @since 2026-02-25
 */
@RestController
@RequestMapping("/system/sys-menu")
@Api(tags = "菜单管理")
public class SysMenuController {
    @Autowired
    private SysMenuService menuService;

    @Autowired
    private SysRoleMenuService roleMenuService;

    @PostMapping(value = "/save")
    @ApiOperation(value = "新增/更新菜单")
    @SaCheckPermission(value = {"sys:menu:add", "sys:menu:edit"}, mode = SaMode.OR)
    @Log(title = "新增/更新菜单")
    public HttpResult save(@RequestBody SysMenu record) {
        if (BeanUtil.isNotEmpty(record)) {
            menuService.saveOrUpdate(record);
            return HttpResult.ok();
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    @ApiOperation(value = "菜单删除")
    @PostMapping(value = "/delete")
    @SaCheckPermission("sys:menu:delete")
    @Log(title = "删除菜单")
    public HttpResult delete(@RequestBody List<Long> records) {
        if (ObjectUtil.isNotEmpty(records)) {
            // 判断删除的菜单下是否有子菜单
            boolean b = menuService.findByParentId(records);
            if (b) {
                return HttpResult.error("请先删除子菜单");
            }
            // 先删除菜单对应的角色权限
            roleMenuService.deleteByMenuId(records);
            menuService.removeByIds(records);
            return HttpResult.ok();
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    @GetMapping(value = "/findNavTree/{name}")
    @ApiOperation(value = "根据用户名查询可查看的导航菜单树")
    @SaCheckPermission("sys:menu:search")
    public HttpResult findNavTree(@ApiParam(value = "用户名") @PathVariable String name) {
        if (ObjectUtil.isNotEmpty(name)) {
            List<SysMenu> list = menuService.findTree(name, 1);
            return HttpResult.ok(list);
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    @ApiOperation(value = "查询所有菜单树")
    @GetMapping(value = "/findMenuTree")
    @SaCheckPermission("sys:menu:search")
    public HttpResult findMenuTree() {
        return HttpResult.ok(menuService.findTree(null, 0));
    }

}
