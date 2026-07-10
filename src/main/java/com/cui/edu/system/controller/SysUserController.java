package com.cui.edu.system.controller;


import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.secure.SaSecureUtil;
import cn.dev33.satoken.secure.totp.SaTotpUtil;
import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import com.cui.edu.common.HttpResult;
import com.cui.edu.common.HttpStatus;
import com.cui.edu.common.PageResult;
import com.cui.edu.common.SysConstants;
import com.cui.edu.system.entity.SysUser;
import com.cui.edu.system.entity.SysUserRole;
import com.cui.edu.system.service.SysUserService;
import com.cui.edu.util.AvoidRepeatRequest;
import com.cui.edu.util.Log;
import com.cui.edu.vo.system.LoginVO;
import com.cui.edu.vo.system.UserVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 用户表 前端控制器
 * </p>
 *
 * @author Cuicui
 * @since 2026-02-25
 */
@RestController
@RequestMapping("/system/sys-user")
@Api(tags = "用户管理")
public class SysUserController {

    @Autowired
    private SysUserService sysUserService;

    @PostMapping(value = "/doLogin")
    @ApiOperation("用户登录")
    @SaIgnore
    @Log(title = "登录")
    @AvoidRepeatRequest(intervalTime = 7, msg = "登录操作频繁，请稍后再试")
    public HttpResult doLogin(@RequestBody LoginVO loginVO) {
        // 校验
        if (BeanUtil.isEmpty(loginVO)) {
            return HttpResult.errorBadRequest();
        }
        SysUser user = sysUserService.findByUsername(loginVO.getUsername());
        if (BeanUtil.isEmpty(user)) {
            return HttpResult.error("用户不存在");
        }
        if (!SaSecureUtil.aesEncrypt(loginVO.getPassword(), user.getSalt()).equals(user.getPassword())) {
            return HttpResult.error("密码错误");
        }
        if (SysConstants.IS_FALSE.equals(user.getStatus())) {
            return HttpResult.error("用户已被禁用");
        }
        // 登录
        StpUtil.login(loginVO.getUsername(), loginVO.getLoginType());
        StpUtil.getSession().set("userId", user.getId());
        // 获取 Token 相关参数
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        tokenInfo.setTag(user.getId().toString());
        // 查询用户角色ID列表
        List<SysUserRole> userRoles = sysUserService.findUserRoles(user.getId());
        List<Long> roleIds = userRoles.stream()
                .map(SysUserRole::getRoleId)
                .collect(Collectors.toList());
        Map<String, Object> result = new HashMap<>();
        result.put("tokenInfo", tokenInfo);
        result.put("roleIds", roleIds);
        result.put("museumId", user.getMuseumId());
        return HttpResult.ok(result);
    }

    /**
     * 添加用户
     * @param user
     * @return
     */
    @PostMapping(value = "/addUser")
    @ApiOperation("添加/更新用户")
    @SaCheckPermission("sys:user:add")
    @Log(title = "添加/更新用户")
    public HttpResult saveUser(@RequestBody SysUser user) {
        if (BeanUtil.isEmpty(user)) {
            return HttpResult.errorBadRequest();
        }
        Map map = sysUserService.saveUser(user);
        if (map.containsKey(SysConstants.MSG)) {
            return HttpResult.error(map.get(SysConstants.MSG).toString());
        }
        return HttpResult.ok();
    }

    /**
     * 禁用启用用户
     * @param id
     * @param status
     * @return
     */
    @GetMapping(value = "/updateUserStatus")
    @ApiOperation("禁用启用用户")
    @SaCheckPermission("sys:user:delete")
    @Log(title = "禁用/启用用户")
    public HttpResult updateUserStatus(@RequestParam Long id, @RequestParam Integer status) {
        sysUserService.updateUserStatus(id, status);
        return HttpResult.ok();
    }

    @PostMapping(value = "/findPage")
    @ApiOperation("分页查询用户")
    @SaCheckPermission("sys:user:search")
    public HttpResult findPage(@RequestBody UserVO vo) {
        if (BeanUtil.isEmpty(vo)) {
            return HttpResult.errorBadRequest();
        }
        PageResult pageResult = sysUserService.findPage(vo);
        return HttpResult.ok(pageResult);
    }


    @GetMapping(value = "/findUserRoles/{id}")
    @ApiOperation(value = "查询用户角色")
    @SaCheckPermission("sys:user:search")
    public HttpResult findUserRoles(@ApiParam(value = "用户id") @PathVariable Long id) {
        if (id != null) {
            List<SysUserRole> list = sysUserService.findUserRoles(id);
            return HttpResult.ok(list);
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

}
