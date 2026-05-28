package com.cui.edu.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cui.edu.system.entity.SysMenu;

import java.util.List;

/**
 * <p>
 * 菜单管理 服务类
 * </p>
 *
 * @author Cuicui
 * @since 2026-02-25
 */
public interface SysMenuService extends IService<SysMenu> {

    /**
     * @return
     * @Author Cuicui
     * @Description 根据父id查询菜单
     **/
    boolean findByParentId(List<Long> records);

    /**
     * @return
     * @Author Cuicui
     * @Description 根据当前用户名查询菜单树
     **/
    List<SysMenu> findTree(String name, int menuType);

}
