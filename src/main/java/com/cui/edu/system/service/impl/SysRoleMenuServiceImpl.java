package com.cui.edu.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cui.edu.system.entity.SysRoleMenu;
import com.cui.edu.system.mapper.SysRoleMenuMapper;
import com.cui.edu.system.service.SysRoleMenuService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 角色菜单表 服务实现类
 * </p>
 *
 * @author Cuicui
 * @since 2026-02-25
 */
@Service
public class SysRoleMenuServiceImpl extends ServiceImpl<SysRoleMenuMapper, SysRoleMenu> implements SysRoleMenuService {

    @Override
    public void deleteByMenuId(List<Long> records) {
        QueryWrapper<SysRoleMenu> queryWrapper = new QueryWrapper<>();
        queryWrapper.in(SysRoleMenu.MENU_ID, records);
        super.remove(queryWrapper);
    }

    @Override
    public void deleteByRoleId(List<Long> records) {
        QueryWrapper<SysRoleMenu> queryWrapper = new QueryWrapper<>();
        queryWrapper.in(SysRoleMenu.ROLE_ID, records);
        super.remove(queryWrapper);
    }

}
