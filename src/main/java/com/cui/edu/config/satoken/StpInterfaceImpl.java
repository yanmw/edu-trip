package com.cui.edu.config.satoken;

import cn.dev33.satoken.stp.StpInterface;
import com.cui.edu.system.entity.SysUser;
import com.cui.edu.system.mapper.SysRoleMapper;
import com.cui.edu.system.service.SysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 自定义权限加载接口实现类
 */
@Component    // 保证此类被 SpringBoot 扫描，完成 Sa-Token 的自定义权限验证扩展
public class StpInterfaceImpl implements StpInterface {

    @Autowired
    private SysRoleMapper sysRoleMapper;

    @Autowired
    private SysUserService sysUserService;

    /**
     * 返回一个账号所拥有的权限码集合
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        SysUser user = sysUserService.findByUsername(String.valueOf(loginId));
        List<String> list = sysRoleMapper.findPermissionByUserId(user.getId());
//        list.add("101");
//        list.add("user.add");
//        list.add("user:add");
//        list.add("user.update");
//        list.add("user.get");
//         list.add("user.delete");
//        list.add("art.*");
        return list;
    }

    /**
     * 返回一个账号所拥有的角色标识集合 (权限与角色可分开校验)
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        SysUser user = sysUserService.findByUsername(String.valueOf(loginId));
        List<String> list = sysRoleMapper.findRoleByUserId(user.getId());
//        list.add("admin");
//        list.add("super-admin");
        return list;
    }

}

