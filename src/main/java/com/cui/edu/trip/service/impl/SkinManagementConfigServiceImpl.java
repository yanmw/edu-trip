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

    /**
     * 分页查询皮肤配置列表
     * <p>
     * 业务流程：
     * 1. 过滤未删除 (is_deleted = 0) 的皮肤记录。
     * 2. 根据传入的博物馆 ID 或启用状态筛选。
     * 3. 关联查询博物馆信息，丰富列表中的博物馆显示名称。
     *
     * @param vo 包含分页和条件的 SkinManagementConfigVO 对象
     * @return 分页结果 PageResult
     */
    @Override
    public PageResult findPage(SkinManagementConfigVO vo) {
        Page<SkinManagementConfig> page = new Page<>(vo.getPageNum(), vo.getPageSize());
        QueryWrapper<SkinManagementConfig> ew = new QueryWrapper<>();
        // 1. 根据传入的博物馆ID精确过滤
        if (vo.getMuseumId() != null) {
            ew.eq(SkinManagementConfig.MUSEUM_ID, vo.getMuseumId());
        }
        // 2. 根据状态精确过滤
        if (vo.getStatus() != null) {
            ew.eq(SkinManagementConfig.STATUS, vo.getStatus());
        }
        // 3. 仅查询未被软删除的记录
        ew.eq(SkinManagementConfig.IS_DELETED, SysConstants.IS_FALSE);
        ew.orderByDesc(SkinManagementConfig.ID);
        page = super.page(page, ew);
        List<SkinManagementConfig> records = page.getRecords();
        
        // 4. 批量查询关联的博物馆，减少数据库的单条循环查询
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
        // 5. 补齐博物馆名称字段
        for (SkinManagementConfig record : records) {
            record.setMuseumName(museumNameMap.get(record.getMuseumId()));
        }
        return PageResultUtil.getPageResult(page);
    }

    /**
     * 批量逻辑删除皮肤配置
     *
     * @param ids 待逻辑删除的配置 ID 集合
     */
    @Override
    public void logicDelete(List<Long> ids) {
        List<SkinManagementConfig> skinManagementConfigList = new ArrayList<>();
        // 1. 组装待删除属性为已删除 (is_deleted = 1)
        for (Long id : ids) {
            SkinManagementConfig skinManagementConfig = new SkinManagementConfig();
            skinManagementConfig.setId(id);
            skinManagementConfig.setIsDeleted(SysConstants.IS_TRUE);
            skinManagementConfigList.add(skinManagementConfig);
        }
        // 2. 批量执行更新
        super.updateBatchById(skinManagementConfigList);
    }

    /**
     * 根据主键 ID 获取未删除的单条皮肤配置详情
     *
     * @param id 配置主键 ID
     * @return 匹配的有效皮肤配置，若不存在返回 null
     */
    @Override
    public SkinManagementConfig findById(Long id) {
        QueryWrapper<SkinManagementConfig> ew = new QueryWrapper<>();
        ew.eq(SkinManagementConfig.ID, id);
        ew.eq(SkinManagementConfig.IS_DELETED, SysConstants.IS_FALSE);
        return super.getOne(ew);
    }

    /**
     * 根据博物馆 ID 获取该博物馆下生效的最新一条皮肤配置
     * <p>
     * 多数情况下，一个博物馆只有一套当前生效的主题皮肤，这里通过按 ID 倒序取 limit 1 拿到当前最新的那条有效配置。
     *
     * @param id 博物馆 ID
     * @return 最新的有效皮肤配置
     */
    @Override
    public SkinManagementConfig findByMuseumId(Long id) {
        QueryWrapper<SkinManagementConfig> ew = new QueryWrapper<>();
        ew.eq(SkinManagementConfig.MUSEUM_ID, id);
        ew.eq(SkinManagementConfig.IS_DELETED, SysConstants.IS_FALSE);
        ew.orderByDesc(SkinManagementConfig.ID);
        ew.last("LIMIT 1"); // 强制只返回最新的一条
        return super.getOne(ew);
    }
}
