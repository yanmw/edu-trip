package com.cui.edu.trip.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.hutool.core.util.ObjectUtil;
import com.cui.edu.common.PageResult;
import com.cui.edu.common.PageResultUtil;
import com.cui.edu.common.SysConstants;
import com.cui.edu.system.entity.Museum;
import com.cui.edu.system.service.MuseumService;
import com.cui.edu.trip.entity.ActivityType;
import com.cui.edu.trip.mapper.ActivityTypeMapper;
import com.cui.edu.trip.service.ActivityTypeService;
import com.cui.edu.vo.trip.ActivityTypeVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 活动类型表 服务实现类
 * </p>
 *
 * @author Cuicui
 * @since 2026-05-29
 */
@Service
public class ActivityTypeServiceImpl extends ServiceImpl<ActivityTypeMapper, ActivityType> implements ActivityTypeService {

    @Autowired
    private MuseumService museumService;

    @Override
    public PageResult findPage(ActivityTypeVO vo) {
        Page<ActivityType> page = new Page<>(vo.getPageNum(), vo.getPageSize());
        QueryWrapper<ActivityType> ew = new QueryWrapper<>();
        if (StringUtils.isNotBlank(vo.getTypeName())) {
            ew.like(ActivityType.TYPE_NAME, vo.getTypeName());
        }
        if (vo.getStatus() != null) {
            ew.eq(ActivityType.STATUS, vo.getStatus());
        }
        ew.eq(ActivityType.IS_DELETED, SysConstants.IS_FALSE);
        ew.orderByDesc(ActivityType.ID);
        page = super.page(page, ew);
        List<ActivityType> activityTypes = page.getRecords();
        Map<Long, String> museumNameMap = activityTypes.stream()
                .map(ActivityType::getMuseumId)
                .filter(ObjectUtil::isNotEmpty)
                .distinct()
                .collect(Collectors.collectingAndThen(Collectors.toList(), museumIds -> {
                    if (ObjectUtil.isEmpty(museumIds)) {
                        return new HashMap<>();
                    }
                    return museumService.listByIds(museumIds).stream()
                            .collect(Collectors.toMap(Museum::getId, Museum::getName));
                }));
        for (ActivityType activityType : activityTypes) {
            activityType.setMuseumName(museumNameMap.get(activityType.getMuseumId()));
        }
        return PageResultUtil.getPageResult(page);
    }

    @Override
    public void logicDelete(List<Long> ids) {
        List<ActivityType> activityTypeList = new ArrayList<>();
        for (Long id : ids) {
            ActivityType activityType = new ActivityType();
            activityType.setId(id);
            activityType.setIsDeleted(SysConstants.IS_TRUE);
            activityTypeList.add(activityType);
        }
        super.updateBatchById(activityTypeList);
    }

    @Override
    public List<ActivityType> findAll() {
        QueryWrapper<ActivityType> ew = new QueryWrapper<>();
        ew.eq(ActivityType.STATUS, SysConstants.IS_TRUE);
        ew.eq(ActivityType.IS_DELETED, SysConstants.IS_FALSE);
        ew.orderByDesc(ActivityType.ID);
        return super.list(ew);
    }
}
