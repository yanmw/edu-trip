package com.cui.edu.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cui.edu.system.entity.SysUserRole;

import java.util.List;

/**
 * <p>
 * 用户角色表 服务类
 * </p>
 *
 * @author Cuicui
 * @since 2026-02-25
 */
public interface SysUserRoleService extends IService<SysUserRole> {

    List<Long> findRolesByUserId(Long userId);

    /**
     * @Author Cuicui
     * @Description  根据角色id和用户id进行删除
     * @Date 2020/2/28 12:43
     * @Param
     * @return
     **/
    void deleteByRoleId(List<Long> records);

    /**
     * 更新用户权限
     * @param userId
     * @param roleId
     */
    void updateUserRole(Long userId, Long roleId);
}
