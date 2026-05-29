package com.cui.edu.trip.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cui.edu.trip.entity.SkinManagementConfig;
import com.cui.edu.trip.mapper.SkinManagementConfigMapper;
import com.cui.edu.trip.service.SkinManagementConfigService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cui.edu.common.PageResult;
import com.cui.edu.common.PageResultUtil;
import com.cui.edu.common.SysConstants;
import com.cui.edu.vo.trip.SkinManagementConfigVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 皮肤管理表 服务实现类
 * </p>
 *
 * @author Cuicui
 * @since 2026-05-29
 */
@Service
public class SkinManagementConfigServiceImpl extends ServiceImpl<SkinManagementConfigMapper, SkinManagementConfig> implements SkinManagementConfigService {

    @Override
    public PageResult findPage(SkinManagementConfigVO vo) {
        Page<SkinManagementConfig> page = new Page<>(vo.getPageNum(), vo.getPageSize());
        QueryWrapper<SkinManagementConfig> ew = new QueryWrapper<>();
        if (vo.getMuseumId() != null) {
            ew.eq(SkinManagementConfig.MUSEUM_ID, vo.getMuseumId());
        }
        if (vo.getStatus() != null) {
            ew.eq(SkinManagementConfig.STATUS, vo.getStatus());
        }
        ew.eq(SkinManagementConfig.IS_DELETED, SysConstants.IS_FALSE);
        ew.orderByDesc(SkinManagementConfig.ID);
        page = super.page(page, ew);
        return PageResultUtil.getPageResult(page);
    }

    @Override
    public void logicDelete(List<Long> ids) {
        List<SkinManagementConfig> skinManagementConfigList = new ArrayList<>();
        for (Long id : ids) {
            SkinManagementConfig skinManagementConfig = new SkinManagementConfig();
            skinManagementConfig.setId(id);
            skinManagementConfig.setIsDeleted(SysConstants.IS_TRUE);
            skinManagementConfigList.add(skinManagementConfig);
        }
        super.updateBatchById(skinManagementConfigList);
    }

    @Override
    public SkinManagementConfig findById(Long id) {
        QueryWrapper<SkinManagementConfig> ew = new QueryWrapper<>();
        ew.eq(SkinManagementConfig.ID, id);
        ew.eq(SkinManagementConfig.IS_DELETED, SysConstants.IS_FALSE);
        return super.getOne(ew);
    }
}
