package com.cui.edu.trip.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cui.edu.common.PageResult;
import com.cui.edu.trip.entity.ActivityTag;
import com.cui.edu.vo.trip.ActivityTagVO;

import java.util.List;

/**
 * <p>
 * 活动标签表 服务类
 * </p>
 *
 * @author Cuicui
 * @since 2026-07-03
 */
public interface ActivityTagService extends IService<ActivityTag> {

    /**
     * 分页查询未删除的活动标签。
     */
    PageResult findPage(ActivityTagVO vo);

    /**
     * 批量逻辑删除活动标签。
     */
    void logicDelete(List<Long> ids);

    /**
     * 查询全部启用且未删除的活动标签。
     */
    List<ActivityTag> findAll();

    /**
     * 查询指定博物馆下启用且未删除的活动标签。
     */
    List<ActivityTag> findAllByMuseumId(Long museumId);
}
