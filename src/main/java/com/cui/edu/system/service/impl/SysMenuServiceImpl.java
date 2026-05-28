package com.cui.edu.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cui.edu.system.entity.SysMenu;
import com.cui.edu.system.entity.SysRole;
import com.cui.edu.system.mapper.SysMenuMapper;
import com.cui.edu.system.mapper.SysRoleMapper;
import com.cui.edu.system.service.SysMenuService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 菜单管理 服务实现类
 * </p>
 *
 * @author Cuicui
 * @since 2026-02-25
 */
@Service
public class SysMenuServiceImpl extends ServiceImpl<SysMenuMapper, SysMenu> implements SysMenuService {

    @Autowired
    private SysMenuMapper menuMapper;

    @Override
    public boolean findByParentId(List<Long> records) {
        QueryWrapper<SysMenu> ew = new QueryWrapper<>();
        ew.in(SysMenu.PARENT_ID, records);
        int count = super.count(ew);
        if (count > 0) {
            return true;
        }
        return false;
    }

    @Override
    public List<SysMenu> findTree(String name, int menuType) {
        List<SysMenu> menuList;
        if (StringUtils.isBlank(name)) {
            menuList = super.list(new QueryWrapper<>());
        } else {
            menuList = menuMapper.findByName(name);
        }
        return menuList;
    }

}
