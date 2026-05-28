package com.cui.edu.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cui.edu.system.entity.SysMenu;
import com.cui.edu.system.entity.SysUserRole;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * 用户角色表 Mapper 接口
 * </p>
 *
 * @author Cuicui
 * @since 2026-02-25
 */
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    List<SysMenu> findRoleMenus(@Param("roleId") Long roleId);
}
