package com.cui.edu.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cui.edu.system.entity.SysMenu;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * 菜单管理 Mapper 接口
 * </p>
 *
 * @author Cuicui
 * @since 2026-02-25
 */
public interface SysMenuMapper extends BaseMapper<SysMenu> {
    /**
     * @return
     * @Description 根据用户名查询菜单
     **/
    List<SysMenu> findByName(@Param("name") String name);
}
