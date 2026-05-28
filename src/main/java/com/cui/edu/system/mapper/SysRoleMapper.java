package com.cui.edu.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cui.edu.system.entity.SysRole;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author Cuicui
 * @since 2026-02-25
 */
public interface SysRoleMapper extends BaseMapper<SysRole> {

    List<String> findPermissionByUserId(@Param("loginId") Object loginId);

    List<String> findRoleByUserId(@Param("loginId") Object loginId);
}
