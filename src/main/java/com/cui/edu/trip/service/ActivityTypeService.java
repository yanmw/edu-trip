package com.cui.edu.trip.service;

import com.cui.edu.trip.entity.ActivityType;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cui.edu.common.PageResult;
import com.cui.edu.vo.trip.ActivityTypeVO;

import java.util.List;

/**
 * <p>
 * 活动类型表 服务类
 * </p>
 *
 * @author Cuicui
 * @since 2026-05-29
 */
public interface ActivityTypeService extends IService<ActivityType> {

    /**
     * 分页查询未删除的活动类型。
     */
    PageResult findPage(ActivityTypeVO vo);

    /**
     * 批量逻辑删除活动类型。
     */
    void logicDelete(List<Long> ids);

    /**
     * 查询全部启用且未删除的活动类型。
     */
    List<ActivityType> findAll();

    /**
     * 查询指定博物馆的活动类型
     */
    List<ActivityType> findAllByMuseumId(Long museumId);

    /**
     * 新增或修改活动类型。
     * <p>
     * 业务规则：
     * 1. 新增时设置默认状态（启用）和删除标记（未删除）。
     * 2. 校验 type_name + museum_id 联合唯一性（忽略已逻辑删除的记录，修改时排除自身）。
     * 3. 通过校验后持久化数据。
     *
     * @param record 活动类型实体
     * @return 校验通过返回 null；校验失败返回具体的错误提示信息
     */
    String saveActivityType(ActivityType record);
}
