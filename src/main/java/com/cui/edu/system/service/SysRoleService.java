package com.cui.edu.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cui.edu.common.PageResult;
import com.cui.edu.system.entity.SysMenu;
import com.cui.edu.system.entity.SysRole;
import com.cui.edu.system.entity.SysRoleMenu;
import com.cui.edu.vo.system.RoleVO;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author Cuicui
 * @since 2026-02-25
 */
public interface SysRoleService extends IService<SysRole> {
    /**
     * @return
     * @Author Cuicui
     * @Description true:存在  false：不存在
     * @Date 2020/2/28 11:44
     * @Param
     **/
    boolean findExistByName(String name);


    /**
     * @return
     * @Author Cuicui
     * @Description true:已使用 false：未使用
     * @Date 2020/2/28 12:02
     * @Param
     **/
    boolean findIsUse(List<Long> list);

    /**
     * @return
     * @Author Cuicui
     * @Description 分页查询
     * @Date 2020/2/28 12:29
     * @Param
     **/
    PageResult findPage(RoleVO vo);

    /**
     * @return
     * @Author Cuicui
     * @Description 根据角色id查询该角色对应的可查看的菜单
     * @Date 2020/2/28 12:58
     * @Param
     **/
    List<SysMenu> findRoleMenus(Long roleId);

    /**
     * @return
     * @Author Cuicui
     * @Description 保存角色菜单
     * @Date 2020/2/28 13:26
     * @Param
     **/
    void saveRoleMenus(List<SysRoleMenu> records);

}
