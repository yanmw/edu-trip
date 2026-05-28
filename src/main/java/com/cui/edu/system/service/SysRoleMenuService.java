package com.cui.edu.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cui.edu.system.entity.SysRoleMenu;

import java.util.List;

/**
 * <p>
 * 角色菜单表 服务类
 * </p>
 *
 * @author Cuicui
 * @since 2026-02-25
 */
public interface SysRoleMenuService extends IService<SysRoleMenu> {

    void deleteByMenuId(List<Long> records);

    void deleteByRoleId(List<Long> records);
}
