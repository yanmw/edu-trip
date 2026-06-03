package com.cui.edu.trip.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.hutool.core.util.ObjectUtil;
import com.cui.edu.trip.entity.ActivityManage;
import com.cui.edu.trip.entity.ActivitySchedule;
import com.cui.edu.trip.mapper.ActivityManageMapper;
import com.cui.edu.trip.service.ActivityManageService;
import com.cui.edu.trip.service.ActivityScheduleService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cui.edu.common.PageResult;
import com.cui.edu.common.PageResultUtil;
import com.cui.edu.common.SysConstants;
import com.cui.edu.system.entity.Museum;
import com.cui.edu.system.service.MuseumService;
import com.cui.edu.vo.trip.ActivityManageVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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

    @Autowired
    private MuseumService museumService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String saveActivityManage(ActivityManage record) {
        if (record.getId() == null) {
            if (record.getStatus() == null) {
                record.setStatus(SysConstants.IS_TRUE);
            }
            if (record.getIsDeleted() == null) {
                record.setIsDeleted(SysConstants.IS_FALSE);
            }
        } else {
            ActivityManage oldActivityManage = super.getById(record.getId());
            String immutableFieldError = validateImmutableFields(oldActivityManage, record);
            if (immutableFieldError != null) {
                return immutableFieldError;
            }
        }
        super.saveOrUpdate(record);
        saveActivitySchedules(record);
        return null;
    }

    @Override
    public PageResult findPage(ActivityManageVO vo) {
        Page<ActivityManage> page = new Page<>(vo.getPageNum(), vo.getPageSize());
        QueryWrapper<ActivityManage> ew = buildQueryWrapper(vo);
        page = super.page(page, ew);
        fillMuseumNames(page.getRecords());
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
        // 活动删除后，场次不物理删除，只同步禁用。
        disableSchedulesByActivityIds(ids);
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
        List<ActivitySchedule> oldScheduleList = listSchedulesByActivityId(record.getId());
        Set<Long> submitScheduleIds = activityScheduleList.stream()
                .map(ActivitySchedule::getId)
                .filter(ObjectUtil::isNotEmpty)
                .collect(Collectors.toSet());
        List<ActivitySchedule> insertList = new ArrayList<>();
        List<ActivitySchedule> updateList = new ArrayList<>();
        List<ActivitySchedule> disableList = new ArrayList<>();
        for (ActivitySchedule activitySchedule : activityScheduleList) {
            activitySchedule.setActivityId(record.getId());
            if (activitySchedule.getId() == null) {
                if (activitySchedule.getStatus() == null) {
                    activitySchedule.setStatus(SysConstants.IS_TRUE);
                }
                insertList.add(activitySchedule);
            } else {
                updateList.add(activitySchedule);
            }
        }
        for (ActivitySchedule oldSchedule : oldScheduleList) {
            if (!submitScheduleIds.contains(oldSchedule.getId())) {
                oldSchedule.setStatus(SysConstants.IS_FALSE);
                disableList.add(oldSchedule);
            }
        }
        if (!insertList.isEmpty()) {
            activityScheduleService.saveBatch(insertList);
        }
        if (!updateList.isEmpty()) {
            activityScheduleService.updateBatchById(updateList);
        }
        if (!disableList.isEmpty()) {
            activityScheduleService.updateBatchById(disableList);
        }
    }

    private void disableSchedulesByActivityIds(List<Long> activityIds) {
        if (activityIds == null || activityIds.isEmpty()) {
            return;
        }
        QueryWrapper<ActivitySchedule> ew = new QueryWrapper<>();
        ew.in(ActivitySchedule.ACTIVITY_ID, activityIds);
        List<ActivitySchedule> activityScheduleList = activityScheduleService.list(ew);
        for (ActivitySchedule activitySchedule : activityScheduleList) {
            activitySchedule.setStatus(SysConstants.IS_FALSE);
        }
        if (!activityScheduleList.isEmpty()) {
            activityScheduleService.updateBatchById(activityScheduleList);
        }
    }

    private List<ActivitySchedule> listSchedulesByActivityId(Long activityId) {
        if (activityId == null) {
            return new ArrayList<>();
        }
        QueryWrapper<ActivitySchedule> ew = new QueryWrapper<>();
        ew.eq(ActivitySchedule.ACTIVITY_ID, activityId);
        return activityScheduleService.list(ew);
    }

    private void fillActivitySchedules(ActivityManage activityManage) {
        if (activityManage == null) {
            return;
        }
        QueryWrapper<ActivitySchedule> ew = new QueryWrapper<>();
        ew.eq(ActivitySchedule.ACTIVITY_ID, activityManage.getId());
        ew.eq(ActivitySchedule.STATUS, SysConstants.IS_TRUE);
        // 场次表不再保存日期，日期范围由活动主表维护，这里只按时间展示场次。
        ew.orderByAsc(ActivitySchedule.START_TIME, ActivitySchedule.END_TIME);
        activityManage.setActivityScheduleList(activityScheduleService.list(ew));
    }

    private void fillActivitySchedules(List<ActivityManage> activityManageList) {
        for (ActivityManage activityManage : activityManageList) {
            fillActivitySchedules(activityManage);
        }
    }

    private void fillMuseumNames(List<ActivityManage> activityManageList) {
        Map<Long, String> museumNameMap = activityManageList.stream()
                .map(ActivityManage::getMuseumId)
                .filter(ObjectUtil::isNotEmpty)
                .distinct()
                .collect(Collectors.collectingAndThen(Collectors.toList(), museumIds -> {
                    if (ObjectUtil.isEmpty(museumIds)) {
                        return new HashMap<>();
                    }
                    return museumService.listByIds(museumIds).stream()
                            .collect(Collectors.toMap(Museum::getId, Museum::getName));
                }));
        for (ActivityManage activityManage : activityManageList) {
            activityManage.setMuseumName(museumNameMap.get(activityManage.getMuseumId()));
        }
    }

    private String validateImmutableFields(ActivityManage oldActivityManage, ActivityManage record) {
        if (oldActivityManage == null) {
            return null;
        }
        if (!Objects.equals(oldActivityManage.getActivityTypeId(), record.getActivityTypeId())) {
            return "活动类型不允许修改，请禁用活动后，新建一个活动内容";
        }
        if (!Objects.equals(oldActivityManage.getPrice(), record.getPrice())) {
            return "活动单价不允许修改，请禁用活动后，新建一个活动内容";
        }
        if (!Objects.equals(oldActivityManage.getMuseumId(), record.getMuseumId())) {
            return "博物馆不允许修改，请禁用活动后，新建一个活动内容";
        }
        if (!Objects.equals(oldActivityManage.getActivityStartDate(), record.getActivityStartDate())) {
            return "活动开始日期不允许修改，请禁用活动后，新建一个活动内容";
        }
        if (!Objects.equals(oldActivityManage.getActivityEndDate(), record.getActivityEndDate())) {
            return "活动结束日期不允许修改，请禁用活动后，新建一个活动内容";
        }
        return null;
    }
}
