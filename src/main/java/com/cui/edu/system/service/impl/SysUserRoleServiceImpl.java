package com.cui.edu.system.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cui.edu.system.entity.SysUserRole;
import com.cui.edu.system.mapper.SysUserRoleMapper;
import com.cui.edu.system.service.SysUserRoleService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 用户角色表 服务实现类
 * </p>
 *
 * @author Cuicui
 * @since 2026-02-25
 */
@Service
public class SysUserRoleServiceImpl extends ServiceImpl<SysUserRoleMapper, SysUserRole> implements SysUserRoleService {

    @Override
    public List<Long> findRolesByUserId(Long userId) {
        QueryWrapper<SysUserRole> wrapper = new QueryWrapper<>();
        wrapper.eq(SysUserRole.USER_ID, userId);
        List<SysUserRole> list = super.list(wrapper);
        return list.stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
    }

    @Override
    public void deleteByRoleId(List<Long> records) {
        if (records != null && records.size() > 0) {
            QueryWrapper<SysUserRole> ew = new QueryWrapper<>();
            ew.in(SysUserRole.ROLE_ID, records);
            super.remove(ew);
        }
    }

    @Override
    public void updateUserRole(Long userId, Long roleId) {
        QueryWrapper<SysUserRole> ew = new QueryWrapper<>();
        ew.eq(SysUserRole.USER_ID, userId);
        List<SysUserRole> list = super.list(ew);
        if(ObjectUtil.isNotEmpty(list)) {
            for (SysUserRole userRole : list) {
                userRole.setRoleId(roleId);
            }
        }
        super.updateBatchById(list);
    }
}
