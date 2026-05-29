package com.cui.edu.system.controller;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cui.edu.common.HttpResult;
import com.cui.edu.common.PageResult;
import com.cui.edu.system.entity.SysMenu;
import com.cui.edu.system.entity.SysRole;
import com.cui.edu.system.entity.SysRoleMenu;
import com.cui.edu.system.service.SysRoleMenuService;
import com.cui.edu.system.service.SysRoleService;
import com.cui.edu.system.service.SysUserRoleService;
import com.cui.edu.common.HttpStatus;
import com.cui.edu.vo.system.RoleVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author Cuicui
 * @since 2026-02-25
 */
@RestController
@RequestMapping("/system/sys-role")
@Api(tags = "角色管理")
public class SysRoleController {

    @Autowired
    private SysRoleService roleService;

    @Autowired
    private SysUserRoleService userRoleService;

    @Autowired
    private SysRoleMenuService roleMenuService;


    @PostMapping(value = "/save")
    @ApiOperation(value = "保存角色")
    public HttpResult save(@RequestBody SysRole record) {
        if (BeanUtil.isNotEmpty(record)) {
//            判断该角色是否已存在
            if (record.getId() == null || record.getId() == 0) {
                boolean b = roleService.findExistByName(record.getName());
                if (b) {
                    return HttpResult.error("角色已存在");
                }
            }
            roleService.saveOrUpdate(record);
            return HttpResult.ok(record.getId());
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    @PostMapping(value = "/delete")
    @ApiOperation(value = "删除角色")
    public HttpResult delete(@RequestBody List<Long> records) {
        if (ObjectUtil.isNotEmpty(records)) {
//            判断删除的角色里是否被使用
            boolean b = roleService.findIsUse(records);
            if (b) {
                return HttpResult.error("删除的角色存在人员，请先清理人员再删除！");
            }
            roleService.removeByIds(records);
            return HttpResult.ok();
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    @PostMapping(value = "/deleteUserRole")
    @ApiOperation(value = "清理角色下的人员及角色菜单")
    public HttpResult deleteUserRole(@RequestBody List<Long> records) {
        if (ObjectUtil.isNotEmpty(records)) {
            userRoleService.deleteByRoleId(records);
            roleMenuService.deleteByRoleId(records);
            return HttpResult.ok();
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    @ApiOperation(value = "角色查询-分页")
    @PostMapping(value = "/findPage")
    public HttpResult findPage(@RequestBody RoleVO vo) {
        if (BeanUtil.isNotEmpty(vo)) {
            PageResult pageResult = roleService.findPage(vo);
            return HttpResult.ok(pageResult);
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    @ApiOperation(value = "查询全部角色")
    @GetMapping(value = "/findAll")
    public HttpResult findAll() {
        List<SysRole> roleList = roleService.list(new QueryWrapper<>());
        return HttpResult.ok(roleList);
    }

    @ApiOperation(value = "查询角色菜单")
    @GetMapping(value = "/findRoleMenus/{roleId}")
    public HttpResult findRoleMenus(@ApiParam(value = "角色id") @PathVariable Long roleId) {
        if (ObjectUtil.isNotEmpty(roleId)) {
            List<SysMenu> menuList = roleService.findRoleMenus(roleId);
            return HttpResult.ok(menuList);
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    @ApiOperation(value = "保存角色-菜单")
    @PostMapping(value = "/saveRoleMenus")
    public HttpResult saveRoleMenus(@RequestBody List<SysRoleMenu> records) {
        if (ObjectUtil.isNotEmpty(records)) {
            roleService.saveRoleMenus(records);
            return HttpResult.ok();
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    @ApiOperation(value = "更新用户-角色")
    @GetMapping(value = "/updateUserRole")
    public HttpResult updateUserRole(@RequestParam Long userId, @RequestParam Long roleId) {
        if (ObjectUtil.isNotEmpty(userId) && ObjectUtil.isNotEmpty(roleId)) {
            userRoleService.updateUserRole(userId, roleId);
            return HttpResult.ok();
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

}
