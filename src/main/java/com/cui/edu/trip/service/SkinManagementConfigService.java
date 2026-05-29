package com.cui.edu.trip.service;

import com.cui.edu.trip.entity.SkinManagementConfig;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cui.edu.common.PageResult;
import com.cui.edu.vo.trip.SkinManagementConfigVO;

import java.util.List;

/**
 * <p>
 * 皮肤管理表 服务类
 * </p>
 *
 * @author Cuicui
 * @since 2026-05-29
 */
public interface SkinManagementConfigService extends IService<SkinManagementConfig> {

    /**
     * 分页查询未删除的皮肤配置。
     */
    PageResult findPage(SkinManagementConfigVO vo);

    /**
     * 批量逻辑删除皮肤配置。
     */
    void logicDelete(List<Long> ids);

    /**
     * 根据ID查询未删除的皮肤配置详情。
     */
    SkinManagementConfig findById(Long id);
}
