package com.cui.edu.trip.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cui.edu.common.PageResult;
import com.cui.edu.common.PageResultUtil;
import com.cui.edu.common.SysConstants;
import com.cui.edu.system.entity.Museum;
import com.cui.edu.system.service.MuseumService;
import com.cui.edu.trip.entity.ActivityTag;
import com.cui.edu.trip.mapper.ActivityTagMapper;
import com.cui.edu.trip.service.ActivityTagService;
import com.cui.edu.vo.trip.ActivityTagVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 活动标签表 服务实现类
 * </p>
 *
 * @author Cuicui
 * @since 2026-07-03
 */
@Service
public class ActivityTagServiceImpl extends ServiceImpl<ActivityTagMapper, ActivityTag> implements ActivityTagService {

    @Autowired
    private MuseumService museumService;

    /**
     * 分页过滤查询活动标签列表
     * <p>
     * 业务流程：
     * 1. 根据标签名称、启用状态和博物馆 ID 进行筛选。
     * 2. 限定查询 is_deleted = 0（未逻辑删除）。
     * 3. 关联查询博物馆表，补齐每一条活动标签对应的博物馆名称。
     *
     * @param vo 包含分页和筛选条件的 ActivityTagVO 对象
     * @return 分页结果 PageResult
     */
    @Override
    public PageResult findPage(ActivityTagVO vo) {
        Page<ActivityTag> page = new Page<>(vo.getPageNum(), vo.getPageSize());
        QueryWrapper<ActivityTag> ew = new QueryWrapper<>();
        // 1. 按标签名称模糊查询
        if (StringUtils.isNotBlank(vo.getTagName())) {
            ew.like(ActivityTag.TAG_NAME, vo.getTagName());
        }
        // 2. 按启用/禁用状态查询
        if (vo.getStatus() != null) {
            ew.eq(ActivityTag.STATUS, vo.getStatus());
        }
        // 3. 按博物馆ID精确查询
        if (vo.getMuseumId() != null) {
            ew.eq(ActivityTag.MUSEUM_ID, vo.getMuseumId());
        }
        // 4. 只查询未删除的活动标签
        ew.eq(ActivityTag.IS_DELETED, SysConstants.IS_FALSE);
        ew.orderByDesc(ActivityTag.ID);
        page = super.page(page, ew);
        List<ActivityTag> activityTags = page.getRecords();

        // 5. 提取去重的博物馆 ID 集合，统一反查博物馆名称并组装为 Map
        Map<Long, String> museumNameMap = activityTags.stream()
                .map(ActivityTag::getMuseumId)
                .filter(ObjectUtil::isNotEmpty)
                .distinct()
                .collect(Collectors.collectingAndThen(Collectors.toList(), museumIds -> {
                    if (ObjectUtil.isEmpty(museumIds)) {
                        return new HashMap<>();
                    }
                    return museumService.listByIds(museumIds).stream()
                            .collect(Collectors.toMap(Museum::getId, Museum::getName));
                }));
        // 6. 回填博物馆名称到列表中
        for (ActivityTag activityTag : activityTags) {
            activityTag.setMuseumName(museumNameMap.get(activityTag.getMuseumId()));
        }
        return PageResultUtil.getPageResult(page);
    }

    /**
     * 批量逻辑删除活动标签
     *
     * @param ids 待逻辑删除的活动标签 ID 列表
     */
    @Override
    public void logicDelete(List<Long> ids) {
        List<ActivityTag> activityTagList = new ArrayList<>();
        // 1. 组装待逻辑删除的数据
        for (Long id : ids) {
            ActivityTag activityTag = new ActivityTag();
            activityTag.setId(id);
            activityTag.setIsDeleted(SysConstants.IS_TRUE); // 置为已删除
            activityTagList.add(activityTag);
        }
        // 2. 执行批量更新
        super.updateBatchById(activityTagList);
    }

    /**
     * 获取所有正处于启用状态且未删除的活动标签列表
     *
     * @return 活动标签列表
     */
    @Override
    public List<ActivityTag> findAll() {
        QueryWrapper<ActivityTag> ew = new QueryWrapper<>();
        ew.eq(ActivityTag.STATUS, SysConstants.IS_TRUE);
        ew.eq(ActivityTag.IS_DELETED, SysConstants.IS_FALSE);
        ew.orderByDesc(ActivityTag.ID);
        return super.list(ew);
    }

    /**
     * 根据博物馆 ID 查询该博物馆下所有已启用且未删除的活动标签列表
     *
     * @param museumId 博物馆 ID
     * @return 匹配的活动标签列表
     */
    @Override
    public List<ActivityTag> findAllByMuseumId(Long museumId) {
        QueryWrapper<ActivityTag> ew = new QueryWrapper<>();
        ew.eq(ActivityTag.STATUS, SysConstants.IS_TRUE);
        ew.eq(ActivityTag.MUSEUM_ID, museumId);
        ew.eq(ActivityTag.IS_DELETED, SysConstants.IS_FALSE);
        ew.orderByDesc(ActivityTag.ID);
        return super.list(ew);
    }
}
