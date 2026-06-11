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
        String requiredFieldError = validateRequiredFields(record);
        if (requiredFieldError != null) {
            return requiredFieldError;
        }
        String participationTypeError = validateParticipationType(record);
        if (participationTypeError != null) {
            return participationTypeError;
        }
        String ageGroupError = validateAgeGroup(record);
        if (ageGroupError != null) {
            return ageGroupError;
        }
        if (record.getId() == null) {
            // 新建活动默认禁用，必须通过审核接口启用。
            record.setStatus(SysConstants.IS_FALSE);
            if (record.getIsDeleted() == null) {
                record.setIsDeleted(SysConstants.IS_FALSE);
            }
        } else {
            ActivityManage oldActivityManage = super.getById(record.getId());
            String immutableFieldError = validateImmutableFields(oldActivityManage, record);
            if (immutableFieldError != null) {
                return immutableFieldError;
            }
            // 修改活动不能绕过审核流程变更状态，状态只由审核/删除等专用接口维护。
            if (oldActivityManage != null) {
                record.setStatus(oldActivityManage.getStatus());
            }
        }
        super.saveOrUpdate(record);
        saveActivitySchedules(record);
        return null;
    }

    @Override
    public String auditActivity(Long id) {
        ActivityManage activityManage = super.getById(id);
        if (activityManage == null || SysConstants.IS_TRUE.equals(activityManage.getIsDeleted())) {
            return "活动不存在";
        }
        if (SysConstants.IS_TRUE.equals(activityManage.getStatus())) {
            return null;
        }
        ActivityManage update = new ActivityManage();
        update.setId(id);
        update.setStatus(SysConstants.IS_TRUE);
        super.updateById(update);
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
    public List<ActivityManage> findByMuseumId(Long museumId, Integer participationType) {
        QueryWrapper<ActivityManage> ew = new QueryWrapper<>();
        ew.eq(ActivityManage.MUSEUM_ID, museumId);
        if (participationType != null) {
            ew.eq(ActivityManage.PARTICIPATION_TYPE, participationType);
        }
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

    private String validateRequiredFields(ActivityManage record) {
        if (ObjectUtil.isEmpty(record)) {
            return "参数有误";
        }
        if (StringUtils.isBlank(record.getActivityName())) {
            return "活动名称不能为空";
        }
        if (ObjectUtil.isEmpty(record.getActivityTypeId())) {
            return "活动类型不能为空";
        }
        if (ObjectUtil.isEmpty(record.getPrice()) || record.getPrice() < 0) {
            return "活动单价不能为空且不能小于0";
        }
        if (ObjectUtil.isEmpty(record.getMuseumId())) {
            return "博物馆ID不能为空";
        }
        if (StringUtils.isBlank(record.getActivityLocation())) {
            return "活动地点不能为空";
        }
        if (ObjectUtil.isEmpty(record.getActivityStartDate())) {
            return "活动开始日期不能为空";
        }
        if (ObjectUtil.isEmpty(record.getActivityEndDate())) {
            return "活动结束日期不能为空";
        }
        if (record.getActivityStartDate().isAfter(record.getActivityEndDate())) {
            return "活动结束日期不能早于活动开始日期";
        }
        if (StringUtils.isBlank(record.getApplicablePeople())) {
            return "适用人群不能为空";
        }
        if (StringUtils.isBlank(record.getRegistrationNotice())) {
            return "报名须知不能为空";
        }
        if (StringUtils.isBlank(record.getContactNumber())) {
            return "联系方式不能为空";
        }
        return validateActivitySchedules(record.getActivityScheduleList());
    }

    private String validateActivitySchedules(List<ActivitySchedule> activityScheduleList) {
        if (ObjectUtil.isEmpty(activityScheduleList)) {
            return "活动场次不能为空";
        }
        for (int i = 0; i < activityScheduleList.size(); i++) {
            ActivitySchedule activitySchedule = activityScheduleList.get(i);
            String schedulePrefix = "第" + (i + 1) + "个活动场次";
            if (ObjectUtil.isEmpty(activitySchedule)) {
                return schedulePrefix + "不能为空";
            }
            if (ObjectUtil.isEmpty(activitySchedule.getStartTime())) {
                return schedulePrefix + "开始时间不能为空";
            }
            if (ObjectUtil.isEmpty(activitySchedule.getEndTime())) {
                return schedulePrefix + "结束时间不能为空";
            }
            if (!activitySchedule.getStartTime().isBefore(activitySchedule.getEndTime())) {
                return schedulePrefix + "结束时间必须晚于开始时间";
            }
            if (ObjectUtil.isEmpty(activitySchedule.getScheduleNumber()) || activitySchedule.getScheduleNumber() <= 0) {
                return schedulePrefix + "场次人数不能为空且必须大于0";
            }
        }
        return null;
    }

    private String validateParticipationType(ActivityManage record) {
        if (record.getParticipationType() == null) {
            return "活动分类不能为空";
        }
        if (!ActivityManage.PARTICIPATION_TYPE_TEAM.equals(record.getParticipationType())
                && !ActivityManage.PARTICIPATION_TYPE_PERSONAL.equals(record.getParticipationType())) {
            return "活动分类参数有误";
        }
        return null;
    }

    private String validateAgeGroup(ActivityManage record) {
        if (record.getAgeGroup() == null) {
            return "年龄分类不能为空";
        }
        if (!ActivityManage.AGE_GROUP_ADULT.equals(record.getAgeGroup())
                && !ActivityManage.AGE_GROUP_CHILD.equals(record.getAgeGroup())) {
            return "年龄分类参数有误";
        }
        return null;
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
