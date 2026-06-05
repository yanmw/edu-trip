package com.cui.edu.trip.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cui.edu.common.PageResult;
import com.cui.edu.common.PageResultUtil;
import com.cui.edu.common.SysConstants;
import com.cui.edu.system.entity.Museum;
import com.cui.edu.system.service.MuseumService;
import com.cui.edu.trip.entity.SkinManagementConfig;
import com.cui.edu.trip.mapper.SkinManagementConfigMapper;
import com.cui.edu.trip.service.SkinManagementConfigService;
import com.cui.edu.vo.trip.SkinManagementConfigVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    @Autowired
    private MuseumService museumService;

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
        List<SkinManagementConfig> records = page.getRecords();
        Map<Long, String> museumNameMap = records.stream()
                .map(SkinManagementConfig::getMuseumId)
                .filter(ObjectUtil::isNotEmpty)
                .distinct()
                .collect(Collectors.collectingAndThen(Collectors.toList(), museumIds -> {
                    if (ObjectUtil.isEmpty(museumIds)) {
                        return new HashMap<>();
                    }
                    return museumService.listByIds(museumIds).stream()
                            .collect(Collectors.toMap(Museum::getId, Museum::getName));
                }));
        for (SkinManagementConfig record : records) {
            record.setMuseumName(museumNameMap.get(record.getMuseumId()));
        }
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

    @Override
    public SkinManagementConfig findByMuseumId(Long id) {
        QueryWrapper<SkinManagementConfig> ew = new QueryWrapper<>();
        ew.eq(SkinManagementConfig.MUSEUM_ID, id);
        ew.eq(SkinManagementConfig.IS_DELETED, SysConstants.IS_FALSE);
        ew.orderByDesc(SkinManagementConfig.ID);
        ew.last("LIMIT 1");
        return super.getOne(ew);
    }
}
