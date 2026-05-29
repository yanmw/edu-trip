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
}
