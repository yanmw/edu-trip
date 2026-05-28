package com.cui.edu.system.service.impl;

import cn.dev33.satoken.secure.SaSecureUtil;
import cn.dev33.satoken.secure.totp.SaTotpUtil;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cui.edu.common.PageResult;
import com.cui.edu.common.PageResultUtil;
import com.cui.edu.system.entity.SysUser;
import com.cui.edu.system.entity.SysUserRole;
import com.cui.edu.system.mapper.SysUserMapper;
import com.cui.edu.system.service.SysUserRoleService;
import com.cui.edu.system.service.SysUserService;
import com.cui.edu.vo.system.UserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <p>
 * 用户表 服务实现类
 * </p>
 *
 * @author Cuicui
 * @since 2026-02-25
 */
@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    @Autowired
    private SysUserRoleService userRoleService;

    @Override
    public SysUser findByUsername(String username) {
        QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(SysUser.USERNAME, username);
        return super.getOne(queryWrapper);
    }

    @Override
    public void saveUser(SysUser user) {
        SysUser sysUser = findByUsername(user.getUsername());
        if (BeanUtil.isNotEmpty(sysUser)) {
            // 用户存在
            if (!sysUser.getPassword().equals(user.getPassword())) {
                // 更新密码
                setPwd(user);
            }
            super.updateById(user);
        } else {
            // 生成密钥盐
            setPwd(user);
            super.save(user);
        }
        // 更新用户角色
        QueryWrapper<SysUserRole> ew = new QueryWrapper<>();
        ew.eq(SysUserRole.USER_ID, user.getId());
        userRoleService.remove(ew);
        List<SysUserRole> userRoles = new ArrayList<>();
        for (Long roleId : user.getRoleIds()) {
            SysUserRole sysUserRole = new SysUserRole();
            sysUserRole.setUserId(user.getId());
            sysUserRole.setRoleId(roleId);
            userRoles.add(sysUserRole);
        }
        userRoleService.saveBatch(userRoles);
    }

    @Override
    public void updateUserStatus(Long id, Integer status) {
        SysUser user = super.getById(id);
        user.setStatus(status);
        super.updateById(user);
        // 退出登录
        StpUtil.logout(user.getUsername());
    }

    @Override
    public PageResult findPage(UserVO vo) {
        Page<SysUser> page = new Page<>(vo.getPageNum(), vo.getPageSize());
        QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.like(ObjectUtil.isNotEmpty(vo.getUsername()), SysUser.USERNAME, vo.getUsername());
        queryWrapper.eq(ObjectUtil.isNotEmpty(vo.getDeptId()), SysUser.DEPT_ID, vo.getDeptId());
        super.page(page, queryWrapper);
        List<SysUser> users = page.getRecords();
        for (SysUser user : users) {
            List<Long> roleIds = userRoleService.findRolesByUserId(user.getId());
            user.setRoleIds(roleIds);
        }
        return PageResultUtil.getPageResult(page);
    }

    @Override
    public List<SysUserRole> findUserRoles(Long id) {
        QueryWrapper<SysUserRole> entityWrapper = new QueryWrapper<>();
        entityWrapper.eq(SysUserRole.USER_ID, id);
        List<SysUserRole> list = userRoleService.list(entityWrapper);
        return list;
    }

    private static void setPwd(SysUser user) {
        // 生成密钥盐
        String salt = SaTotpUtil.generateSecretKey();
        // 加密
        String ciphertext = SaSecureUtil.aesEncrypt(user.getPassword(), salt);
        user.setSalt(salt);
        user.setPassword(ciphertext);
    }
}
