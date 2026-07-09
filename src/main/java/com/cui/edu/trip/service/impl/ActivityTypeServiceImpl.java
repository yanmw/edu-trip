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

import java.util.*;
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

    /**
     * 分页过滤查询活动类型列表
     * <p>
     * 业务流程：
     * 1. 根据分类名称、启用状态和博物馆 ID 进行筛选。
     * 2. 限定查询 is_deleted = 0（未逻辑删除）。
     * 3. 关联查询博物馆表，补齐每一条活动类型对应的博物馆名称。
     *
     * @param vo 包含分页和筛选条件的 ActivityTypeVO 对象
     * @return 分页结果 PageResult
     */
    @Override
    public PageResult findPage(ActivityTypeVO vo) {
        Page<ActivityType> page = new Page<>(vo.getPageNum(), vo.getPageSize());
        QueryWrapper<ActivityType> ew = new QueryWrapper<>();
        // 1. 按活动类型名称模糊查询
        if (StringUtils.isNotBlank(vo.getTypeName())) {
            ew.like(ActivityType.TYPE_NAME, vo.getTypeName());
        }
        // 2. 按启用/禁用状态查询
        if (vo.getStatus() != null) {
            ew.eq(ActivityType.STATUS, vo.getStatus());
        }
        // 3. 按博物馆ID查询
        if (vo.getMuseumId() != null) {
            ew.eq(ActivityType.MUSEUM_ID, vo.getMuseumId());
        }
        // 4. 只查询未删除的活动类型
        ew.eq(ActivityType.IS_DELETED, SysConstants.IS_FALSE);
        ew.orderByDesc(ActivityType.ID);
        page = super.page(page, ew);
        List<ActivityType> activityTypes = page.getRecords();
        
        // 5. 提取去重的博物馆 ID 集合，统一反查博物馆名称并组装为 Map
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
        // 6. 回填博物馆名称到列表中
        for (ActivityType activityType : activityTypes) {
            activityType.setMuseumName(museumNameMap.get(activityType.getMuseumId()));
        }
        return PageResultUtil.getPageResult(page);
    }

    /**
     * 批量逻辑删除活动类型
     *
     * @param ids 待逻辑删除的活动类型 ID 列表
     */
    @Override
    public void logicDelete(List<Long> ids) {
        List<ActivityType> activityTypeList = new ArrayList<>();
        // 1. 组装待逻辑删除的数据
        for (Long id : ids) {
            ActivityType activityType = new ActivityType();
            activityType.setId(id);
            activityType.setIsDeleted(SysConstants.IS_TRUE); // 置为已删除
            activityTypeList.add(activityType);
        }
        // 2. 执行批量更新
        super.updateBatchById(activityTypeList);
    }

    /**
     * 获取所有正处于启用状态且未删除的活动类型列表
     *
     * @return 活动类型列表
     */
    @Override
    public List<ActivityType> findAll() {
        QueryWrapper<ActivityType> ew = new QueryWrapper<>();
        // 查询有效激活的活动类别
        ew.eq(ActivityType.STATUS, SysConstants.IS_TRUE);
        ew.eq(ActivityType.IS_DELETED, SysConstants.IS_FALSE);
        ew.orderByDesc(ActivityType.ID);
        return super.list(ew);
    }

    /**
     * 根据博物馆 ID 查询该博物馆下所有已启用且未删除的活动类型列表
     *
     * @param museumId 博物馆 ID
     * @return 匹配的活动类型列表
     */
    @Override
    public List<ActivityType> findAllByMuseumId(Long museumId) {
        QueryWrapper<ActivityType> ew = new QueryWrapper<>();
        ew.eq(ActivityType.STATUS, SysConstants.IS_TRUE);
        ew.eq(ActivityType.MUSEUM_ID, museumId);
        ew.eq(ActivityType.IS_DELETED, SysConstants.IS_FALSE);
        ew.orderByDesc(ActivityType.ID);
        return super.list(ew);
    }

    /**
     * 新增或修改活动类型
     * <p>
     * 业务流程：
     * 1. 新增时设置默认状态（启用）和删除标记（未删除）。
     * 2. 校验 type_name + museum_id 联合唯一性（忽略已逻辑删除的记录，修改时排除自身）。
     * 3. 通过校验后持久化数据。
     *
     * @param record 活动类型实体
     * @return 校验通过返回 null；校验失败返回具体的错误提示信息
     */
    @Override
    public String saveActivityType(ActivityType record) {
        // 1. 新增时设置默认值
        if (record.getId() == null) {
            if (record.getStatus() == null) {
                record.setStatus(SysConstants.IS_TRUE);
            }
            if (record.getIsDeleted() == null) {
                record.setIsDeleted(SysConstants.IS_FALSE);
            }
        }
        // 2. 校验 type_name + museum_id 联合唯一性
        String uniqueError = checkUnique(record);
        if (uniqueError != null) {
            return uniqueError;
        }
        // 3. 持久化
        super.saveOrUpdate(record);
        return null;
    }

    /**
     * 校验 type_name + museum_id 联合唯一性（忽略已逻辑删除的记录）
     * <p>
     * 业务规则：同一博物馆下，活动类型名称不允许重复。
     * 修改场景下，需排除当前记录自身（通过 id != currentId）。
     *
     * @param record 待保存的活动类型实体
     * @return 校验通过返回 null；存在重复记录返回错误提示信息
     */
    private String checkUnique(ActivityType record) {
        if (StringUtils.isBlank(record.getTypeName()) || ObjectUtil.isEmpty(record.getMuseumId())) {
            // 必填字段缺失时不做唯一性校验，交由后续业务逻辑处理
            return null;
        }
        QueryWrapper<ActivityType> ew = new QueryWrapper<>();
        ew.eq(ActivityType.TYPE_NAME, record.getTypeName());
        ew.eq(ActivityType.MUSEUM_ID, record.getMuseumId());
        ew.eq(ActivityType.IS_DELETED, SysConstants.IS_FALSE);
        // 修改场景：排除当前记录自身，避免将自身判断为重复
        if (record.getId() != null) {
            ew.ne(ActivityType.ID, record.getId());
        }
        long count = super.count(ew);
        if (count > 0) {
            return "该博物馆下已存在同名活动类型\"" + record.getTypeName() + "\"，请修改后重试";
        }
        return null;
    }
}

