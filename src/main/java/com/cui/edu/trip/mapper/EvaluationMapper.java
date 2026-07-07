package com.cui.edu.trip.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cui.edu.trip.entity.Evaluation;
import com.cui.edu.vo.trip.EvaluationVO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * <p>
 * 评价表 Mapper 接口
 * </p>
 *
 * @author Cuicui
 * @since 2026-05-29
 */
public interface EvaluationMapper extends BaseMapper<Evaluation> {

    /**
     * 多表联查评价分页列表。
     * <p>
     * 关联 order_detail 获取 activity_id，再关联 activity_manage 获取活动名称。
     * 支持按活动名称（模糊）、提交时间范围、总体评分等级过滤。
     *
     * @param page          分页参数（由 MyBatis-Plus 自动注入 LIMIT/OFFSET）
     * @param vo            查询条件（activityName、overallScore、orderId）
     * @param startDateTime 提交时间起始（由 Service 层调用 DateTimeUtils.getStartDateTime 转换）
     * @param endDateTime   提交时间结束（由 Service 层调用 DateTimeUtils.getEndDateTime 转换）
     * @return 带活动信息的评价分页结果
     */
    Page<Evaluation> findPageWithActivity(Page<Evaluation> page,
                                          @Param("vo") EvaluationVO vo,
                                          @Param("startDateTime") LocalDateTime startDateTime,
                                          @Param("endDateTime") LocalDateTime endDateTime);
}
