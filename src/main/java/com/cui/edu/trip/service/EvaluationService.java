package com.cui.edu.trip.service;

import com.cui.edu.trip.entity.Evaluation;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cui.edu.common.PageResult;
import com.cui.edu.vo.trip.EvaluationVO;

import java.util.List;

/**
 * <p>
 * 评价表 服务类
 * </p>
 *
 * @author Cuicui
 * @since 2026-05-29
 */
public interface EvaluationService extends IService<Evaluation> {

    /**
     * 分页查询未删除的评价。
     */
    PageResult findPage(EvaluationVO vo);

    /**
     * 批量逻辑删除评价。
     */
    void logicDelete(List<Long> ids);

    /**
     * 根据订单ID查询最新一条未删除评价。
     */
    Evaluation findByOrderId(Long orderId);
}
