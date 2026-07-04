package com.cui.edu.trip.service;

import com.cui.edu.trip.entity.Evaluation;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cui.edu.common.HttpResult;
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
     * 新增评价（含入参校验、一单只评一次校验、初始化默认字段）。
     *
     * @param record 评价实体
     * @return HttpResult 成功时返回评价主键 ID，校验不通过时返回错误信息
     */
    HttpResult saveEvaluation(Evaluation record);

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
