package com.cui.edu.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cui.edu.common.PageResult;
import com.cui.edu.common.PageResultUtil;
import com.cui.edu.system.entity.SysMenu;
import com.cui.edu.system.entity.SysRole;
import com.cui.edu.system.entity.SysRoleMenu;
import com.cui.edu.system.entity.SysUserRole;
import com.cui.edu.system.mapper.SysRoleMapper;
import com.cui.edu.system.mapper.SysRoleMenuMapper;
import com.cui.edu.system.mapper.SysUserRoleMapper;
import com.cui.edu.system.service.SysRoleService;
import com.cui.edu.vo.system.RoleVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author Cuicui
 * @since 2026-02-25
 */
@Service
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements SysRoleService {

    @Autowired
    private SysUserRoleMapper userRoleMapper;

    @Autowired
    private SysRoleMenuMapper roleMenuMapper;

    @Override
    public boolean findExistByName(String name) {
        QueryWrapper<SysRole> ew = new QueryWrapper<>();
        ew.eq(SysRole.NAME, name);
        int count = super.count(ew);
        if (count > 0) {
            return true;
        }
        return false;

    }


    @Override
    public boolean findIsUse(List<Long> list) {
        QueryWrapper<SysUserRole> ew = new QueryWrapper<>();
        ew.in(SysUserRole.ROLE_ID, list);
        int count = userRoleMapper.selectCount(ew);
        if (count > 0) {
            return true;
        }
        return false;
    }

    @Override
    public PageResult findPage(RoleVO vo) {
        Page<SysRole> page = new Page<>(vo.getPageNum(), vo.getPageSize());
        QueryWrapper<SysRole> ew = new QueryWrapper<>();
        if (StringUtils.isNotBlank(vo.getName())) {
            ew.like("name", vo.getName());
        }
        ew.orderByDesc(SysRole.ID);
        page = super.page(page, ew);
        PageResult pageResult = PageResultUtil.getPageResult(page);
        return pageResult;
    }

    @Override
    public List<SysMenu> findRoleMenus(Long roleId) {
        List<SysMenu> menuList = userRoleMapper.findRoleMenus(roleId);
        return menuList;
    }

    @Override
    public void saveRoleMenus(List<SysRoleMenu> records) {
        QueryWrapper<SysRoleMenu> ew = new QueryWrapper<>();
        ew.eq(SysRoleMenu.ROLE_ID, records.get(0).getRoleId());
        roleMenuMapper.delete(ew);
        for (SysRoleMenu roleMenu : records) {
            roleMenuMapper.insert(roleMenu);
        }
    }

}
