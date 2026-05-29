package com.cui.edu.trip.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cui.edu.trip.entity.ActivityManage;
import com.cui.edu.trip.entity.ActivitySchedule;
import com.cui.edu.trip.mapper.ActivityManageMapper;
import com.cui.edu.trip.service.ActivityManageService;
import com.cui.edu.trip.service.ActivityScheduleService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cui.edu.common.PageResult;
import com.cui.edu.common.PageResultUtil;
import com.cui.edu.common.SysConstants;
import com.cui.edu.vo.trip.ActivityManageVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * <p>
 * 活动管理表 服务实现类
 * </p>
 *
 * @author Cuicui
 * @since 2026-05-29
 */
@Service
public class ActivityManageServiceImpl extends ServiceImpl<ActivityManageMapper, ActivityManage> implements ActivityManageService {

    @Autowired
    private ActivityScheduleService activityScheduleService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveActivityManage(ActivityManage record) {
        if (record.getId() == null) {
            if (record.getStatus() == null) {
                record.setStatus(SysConstants.IS_TRUE);
            }
            if (record.getIsDeleted() == null) {
                record.setIsDeleted(SysConstants.IS_FALSE);
            }
        } else {
            ActivityManage oldActivityManage = super.getById(record.getId());
            // 活动单价不允许修改，价格变化时由 Controller 返回业务提示。
            if (oldActivityManage != null && !Objects.equals(oldActivityManage.getPrice(), record.getPrice())) {
                return false;
            }
        }
        super.saveOrUpdate(record);
        saveActivitySchedules(record);
        return true;
    }

    @Override
    public PageResult findPage(ActivityManageVO vo) {
        Page<ActivityManage> page = new Page<>(vo.getPageNum(), vo.getPageSize());
        QueryWrapper<ActivityManage> ew = buildQueryWrapper(vo);
        page = super.page(page, ew);
        return PageResultUtil.getPageResult(page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void logicDelete(List<Long> ids) {
        List<ActivityManage> activityManageList = new ArrayList<>();
        for (Long id : ids) {
            ActivityManage activityManage = new ActivityManage();
            activityManage.setId(id);
            activityManage.setIsDeleted(SysConstants.IS_TRUE);
            activityManageList.add(activityManage);
        }
        super.updateBatchById(activityManageList);
        // 活动删除后，场次不再单独保留。
        removeSchedulesByActivityIds(ids);
    }

    @Override
    public ActivityManage findById(Long id) {
        QueryWrapper<ActivityManage> ew = new QueryWrapper<>();
        ew.eq(ActivityManage.ID, id);
        ew.eq(ActivityManage.IS_DELETED, SysConstants.IS_FALSE);
        ActivityManage activityManage = super.getOne(ew);
        fillActivitySchedules(activityManage);
        return activityManage;
    }

    @Override
    public List<ActivityManage> findByMuseumId(Long museumId) {
        QueryWrapper<ActivityManage> ew = new QueryWrapper<>();
        ew.eq(ActivityManage.MUSEUM_ID, museumId);
        ew.eq(ActivityManage.STATUS, SysConstants.IS_TRUE);
        ew.eq(ActivityManage.IS_DELETED, SysConstants.IS_FALSE);
        ew.orderByDesc(ActivityManage.ID);
        List<ActivityManage> activityManageList = super.list(ew);
        fillActivitySchedules(activityManageList);
        return activityManageList;
    }

    private QueryWrapper<ActivityManage> buildQueryWrapper(ActivityManageVO vo) {
        QueryWrapper<ActivityManage> ew = new QueryWrapper<>();
        if (StringUtils.isNotBlank(vo.getActivityName())) {
            ew.like(ActivityManage.ACTIVITY_NAME, vo.getActivityName());
        }
        if (vo.getActivityTypeId() != null) {
            ew.eq(ActivityManage.ACTIVITY_TYPE_ID, vo.getActivityTypeId());
        }
        if (vo.getIsHot() != null) {
            ew.eq(ActivityManage.IS_HOT, vo.getIsHot());
        }
        if (vo.getMuseumId() != null) {
            ew.eq(ActivityManage.MUSEUM_ID, vo.getMuseumId());
        }
        if (vo.getStatus() != null) {
            ew.eq(ActivityManage.STATUS, vo.getStatus());
        }
        ew.eq(ActivityManage.IS_DELETED, SysConstants.IS_FALSE);
        ew.orderByDesc(ActivityManage.ID);
        return ew;
    }

    private void saveActivitySchedules(ActivityManage record) {
        List<ActivitySchedule> activityScheduleList = record.getActivityScheduleList();
        if (activityScheduleList == null) {
            return;
        }
        // 前端提交的是活动场次的完整快照，先清旧数据再保存新数据。
        removeSchedulesByActivityIds(Collections.singletonList(record.getId()));
        for (ActivitySchedule activitySchedule : activityScheduleList) {
            activitySchedule.setId(null);
            activitySchedule.setActivityId(record.getId());
        }
        if (!activityScheduleList.isEmpty()) {
            activityScheduleService.saveBatch(activityScheduleList);
        }
    }

    private void removeSchedulesByActivityIds(List<Long> activityIds) {
        if (activityIds == null || activityIds.isEmpty()) {
            return;
        }
        QueryWrapper<ActivitySchedule> ew = new QueryWrapper<>();
        ew.in(ActivitySchedule.ACTIVITY_ID, activityIds);
        activityScheduleService.remove(ew);
    }

    private void fillActivitySchedules(ActivityManage activityManage) {
        if (activityManage == null) {
            return;
        }
        QueryWrapper<ActivitySchedule> ew = new QueryWrapper<>();
        ew.eq(ActivitySchedule.ACTIVITY_ID, activityManage.getId());
        // 详情和列表统一按日期、开始时间展示场次。
        ew.orderByAsc(ActivitySchedule.ACTIVITY_DATE, ActivitySchedule.START_TIME);
        activityManage.setActivityScheduleList(activityScheduleService.list(ew));
    }

    private void fillActivitySchedules(List<ActivityManage> activityManageList) {
        for (ActivityManage activityManage : activityManageList) {
            fillActivitySchedules(activityManage);
        }
    }
}
