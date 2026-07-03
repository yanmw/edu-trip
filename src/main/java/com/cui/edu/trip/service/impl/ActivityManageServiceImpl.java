package com.cui.edu.trip.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.hutool.core.util.ObjectUtil;
import com.cui.edu.trip.entity.ActivityManage;
import com.cui.edu.trip.entity.ActivitySchedule;
import com.cui.edu.trip.mapper.ActivityManageMapper;
import com.cui.edu.trip.mapper.OrderDetailMapper;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    /**
     * 保存或更新活动信息
     * <p>
     * 业务规则：
     * 1. 进行基础参数的完整性校验（validateRequiredFields）。
     * 2. 校验参与形式（团队/个人）和适用年龄分组是否合法。
     * 3. 区分新增与更新逻辑：
     *    a. 新增活动：初始化状态为禁用 (status=0)，必须通过审核上架接口启用；若未设定删除标记，默认设为未删除。
     *    b. 更新活动：校验只读属性是否被篡改（validateImmutableFields，如类型、单价、博物馆、排期时间均不可改，改之则需新建活动）；状态沿用原活动状态，防篡改。
     * 4. 持久化活动信息，并同步更新该活动所属的场次排期列表 (saveActivitySchedules)。
     *
     * @param record 活动信息实体
     * @return 业务校验通过返回 null，若校验不通过则返回具体的错误提示信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String saveActivityManage(ActivityManage record) {
        // 1. 校验必填属性和关联场次数据
        String requiredFieldError = validateRequiredFields(record);
        if (requiredFieldError != null) {
            return requiredFieldError;
        }
        // 2. 校验参与类别
        String participationTypeError = validateParticipationType(record);
        if (participationTypeError != null) {
            return participationTypeError;
        }
        // 3. 校验人群年龄分类
        String ageGroupError = validateAgeGroup(record);
        if (ageGroupError != null) {
            return ageGroupError;
        }
        // 4. 判断是新增还是修改
        if (record.getId() == null) {
            // 4.1 新建活动默认处于禁用状态，由管理员审核后启用
            record.setStatus(SysConstants.IS_FALSE);
            if (record.getIsDeleted() == null) {
                record.setIsDeleted(SysConstants.IS_FALSE);
            }
        } else {
            // 4.2 校验只读字段：活动一旦建立并发布，其单价、博物馆、起止时间等不允许再修改，以保证历史订单的可追溯性
            ActivityManage oldActivityManage = super.getById(record.getId());
            String immutableFieldError = validateImmutableFields(oldActivityManage, record);
            if (immutableFieldError != null) {
                return immutableFieldError;
            }
            // 4.3 状态受专用审核接口管控，此处防修改时篡改状态
            if (oldActivityManage != null) {
                record.setStatus(oldActivityManage.getStatus());
            }
        }
        // 5. 保存活动并保存/更新关联的场次列表
        super.saveOrUpdate(record);
        saveActivitySchedules(record);
        return null;
    }

    /**
     * 更新活动状态（启用/下架禁用活动）
     *
     * @param id     活动主键 ID
     * @param status 目标状态值：1 启用，0 禁用
     * @return 校验通过并更新成功返回 null，否则返回具体的错误信息
     */
    @Override
    public String updateStatus(Long id, Integer status) {
        // 1. 状态参数值范围校验
        if (!SysConstants.IS_TRUE.equals(status) && !SysConstants.IS_FALSE.equals(status)) {
            return "活动状态参数有误";
        }
        // 2. 校验目标活动是否存在
        ActivityManage activityManage = super.getById(id);
        if (activityManage == null || SysConstants.IS_TRUE.equals(activityManage.getIsDeleted())) {
            return "活动不存在";
        }
        // 3. 状态一致时无需重复操作
        if (status.equals(activityManage.getStatus())) {
            return null;
        }
        // 4. 更新上下架状态
        ActivityManage update = new ActivityManage();
        update.setId(id);
        update.setStatus(status);
        super.updateById(update);
        return null;
    }

    /**
     * 分页过滤查询活动列表
     *
     * @param vo 包含过滤及分页条件的视图对象
     * @return 分页结果 PageResult 包装，已补充博物馆的显示名称
     */
    @Override
    public PageResult findPage(ActivityManageVO vo) {
        Page<ActivityManage> page = new Page<>(vo.getPageNum(), vo.getPageSize());
        // 1. 构建 MyBatis-Plus 查询 wrapper
        QueryWrapper<ActivityManage> ew = buildQueryWrapper(vo);
        // 2. 分页查询
        page = super.page(page, ew);
        // 3. 补充各记录对应的博物馆名称，供前台页面渲染
        fillMuseumNames(page.getRecords());
        return PageResultUtil.getPageResult(page);
    }

    /**
     * 批量逻辑删除活动
     * <p>
     * 业务流程：
     * 1. 批量更新活动表的 is_deleted 标志为 1。
     * 2. 活动被逻辑删除后，其名下绑定的所有活动场次 (ActivitySchedule) 均需同步置为禁用状态，防止被错误购买。
     *
     * @param ids 待逻辑删除的活动 ID 列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void logicDelete(List<Long> ids) {
        List<ActivityManage> activityManageList = new ArrayList<>();
        // 1. 循环构建待逻辑删除的活动载荷
        for (Long id : ids) {
            ActivityManage activityManage = new ActivityManage();
            activityManage.setId(id);
            activityManage.setIsDeleted(SysConstants.IS_TRUE);
            activityManageList.add(activityManage);
        }
        // 2. 执行批量删除更新
        super.updateBatchById(activityManageList);
        // 3. 同步禁用该批活动关联的所有场次排期
        disableSchedulesByActivityIds(ids);
    }

    /**
     * 根据主键 ID 查询处于有效状态的活动详情，并填充激活的场次排期列表
     *
     * @param id 活动主键 ID
     * @return 活动实体详情，若不存在或已被删除返回 null
     */
    @Override
    public ActivityManage findById(Long id) {
        QueryWrapper<ActivityManage> ew = new QueryWrapper<>();
        ew.eq(ActivityManage.ID, id);
        ew.eq(ActivityManage.IS_DELETED, SysConstants.IS_FALSE);
        ActivityManage activityManage = super.getOne(ew);
        // 补齐该活动关联的所有激活状态的排期场次
        fillActivitySchedules(activityManage);
        return activityManage;
    }

    /**
     * 查询指定博物馆名下可供预约的所有已上架活动列表（用于小程序等前端展示）
     * <p>
     * 若传入 appointmentDate，则对每个活动的每个场次回填该日期已预约人数（bookedCount）。
     * 已预约人数 = 订单详情表中状态为"支付成功(10)"且关联主订单预约日期匹配的记录数。
     *
     * @param museumId          博物馆 ID
     * @param participationType 可选：参与形式 (个人/团队)
     * @param activityTypeId    可选：活动类型 ID
     * @param appointmentDate   可选：预约日期，格式 yyyy-MM-dd；传入后场次返回 bookedCount 字段
     * @return 匹配的有效活动列表，且带排期场次详情
     */
    @Override
    public List<ActivityManage> findByMuseumId(Long museumId, Integer participationType, Long activityTypeId, LocalDate appointmentDate) {
        QueryWrapper<ActivityManage> ew = new QueryWrapper<>();
        ew.eq(ActivityManage.MUSEUM_ID, museumId);
        if (participationType != null) {
            ew.eq(ActivityManage.PARTICIPATION_TYPE, participationType);
        }
        if (activityTypeId != null) {
            ew.eq(ActivityManage.ACTIVITY_TYPE_ID, activityTypeId);
        }
        // 仅查询处于"已启用上架 (1)"且"未逻辑删除 (0)"状态的活动
        ew.eq(ActivityManage.STATUS, SysConstants.IS_TRUE);
        ew.eq(ActivityManage.IS_DELETED, SysConstants.IS_FALSE);
        ew.orderByDesc(ActivityManage.ID);
        List<ActivityManage> activityManageList = super.list(ew);
        // 补齐每个活动的排期场次数据
        fillActivitySchedules(activityManageList);
        // 若传入预约日期，则回填每个场次的已预约人数
        if (appointmentDate != null) {
            fillBookedCount(activityManageList, museumId, appointmentDate);
        }
        return activityManageList;
    }

    // ==================== 私有辅助方法 ====================

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
        if (vo.getParticipationType() != null) {
            ew.eq(ActivityManage.PARTICIPATION_TYPE, vo.getParticipationType());
        }
        // 按标签ID过滤：tag_ids 字段存储逗号分隔的标签ID，使用 FIND_IN_SET 精确匹配单个标签ID
        if (vo.getTagId() != null) {
            ew.apply("FIND_IN_SET({0}, tag_ids) > 0", vo.getTagId());
        }
        ew.eq(ActivityManage.IS_DELETED, SysConstants.IS_FALSE);
        ew.orderByDesc(ActivityManage.ID);
        return ew;
    }

    /**
     * 批量回填所有活动场次在指定预约日期的已预约人数（bookedCount）。
     * <p>
     * 统计口径：订单详情表 order_status = 10（支付成功），联表主订单 appointment_date = 指定日期。
     * 采用"先收集所有场次ID，批量查询，再按场次ID分组聚合"的方式，避免 N+1 查询。
     *
     * @param activityManageList 活动列表（场次列表已填充）
     * @param museumId           博物馆 ID（与订单详情表联合过滤，防跨馆干扰）
     * @param appointmentDate    预约日期
     */
    private void fillBookedCount(List<ActivityManage> activityManageList, Long museumId, LocalDate appointmentDate) {
        if (ObjectUtil.isEmpty(activityManageList)) {
            return;
        }
        // 1. 收集所有场次，构建 scheduleId -> ActivitySchedule 的映射
        Map<Long, ActivitySchedule> scheduleMap = new HashMap<>();
        for (ActivityManage activity : activityManageList) {
            List<ActivitySchedule> schedules = activity.getActivityScheduleList();
            if (ObjectUtil.isNotEmpty(schedules)) {
                for (ActivitySchedule schedule : schedules) {
                    scheduleMap.put(schedule.getId(), schedule);
                }
            }
        }
        if (scheduleMap.isEmpty()) {
            return;
        }
        // 2. 统计口径：订单详情 order_status = 10（支付成功）
        List<Integer> detailStatuses = Collections.singletonList(10);
        // 主订单状态不做额外限制（以订单详情状态为准），传空列表跳过主订单状态过滤
        List<Integer> orderStatuses = Collections.emptyList();

        // 3. 逐场次调用已有的 countBookedQuantity 方法统计已预约人数，并回填
        //    场次数量通常较小（每个活动 1~5 个场次），整体调用次数可控。
        for (ActivityManage activity : activityManageList) {
            List<ActivitySchedule> schedules = activity.getActivityScheduleList();
            if (ObjectUtil.isEmpty(schedules)) {
                continue;
            }
            for (ActivitySchedule schedule : schedules) {
                int booked = orderDetailMapper.countBookedQuantity(
                        museumId,
                        activity.getId(),
                        schedule.getId(),
                        appointmentDate,
                        orderStatuses,
                        detailStatuses
                );
                schedule.setBookedCount(booked);
            }
        }
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
