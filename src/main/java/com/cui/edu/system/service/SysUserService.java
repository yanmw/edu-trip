package com.cui.edu.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cui.edu.common.PageResult;
import com.cui.edu.system.entity.SysUser;
import com.cui.edu.system.entity.SysUserRole;
import com.cui.edu.vo.system.UserVO;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 用户表 服务类
 * </p>
 *
 * @author Cuicui
 * @since 2026-02-25
 */
public interface SysUserService extends IService<SysUser> {

    SysUser findByUsername(String username);

    Map saveUser(SysUser user);

    void updateUserStatus(Long id, Integer status);

    PageResult findPage(UserVO vo);

    List<SysUserRole> findUserRoles(Long id);
}
