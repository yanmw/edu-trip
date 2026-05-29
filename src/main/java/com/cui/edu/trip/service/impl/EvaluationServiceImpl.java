package com.cui.edu.trip.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cui.edu.trip.entity.Evaluation;
import com.cui.edu.trip.mapper.EvaluationMapper;
import com.cui.edu.trip.service.EvaluationService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cui.edu.common.PageResult;
import com.cui.edu.common.PageResultUtil;
import com.cui.edu.common.SysConstants;
import com.cui.edu.vo.trip.EvaluationVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 评价表 服务实现类
 * </p>
 *
 * @author Cuicui
 * @since 2026-05-29
 */
@Service
public class EvaluationServiceImpl extends ServiceImpl<EvaluationMapper, Evaluation> implements EvaluationService {

    @Override
    public PageResult findPage(EvaluationVO vo) {
        Page<Evaluation> page = new Page<>(vo.getPageNum(), vo.getPageSize());
        QueryWrapper<Evaluation> ew = new QueryWrapper<>();
        if (vo.getOrderId() != null) {
            ew.eq(Evaluation.ORDER_ID, vo.getOrderId());
        }
        ew.eq(Evaluation.IS_DELETED, SysConstants.IS_FALSE);
        ew.orderByDesc(Evaluation.ID);
        page = super.page(page, ew);
        return PageResultUtil.getPageResult(page);
    }

    @Override
    public void logicDelete(List<Long> ids) {
        List<Evaluation> evaluationList = new ArrayList<>();
        for (Long id : ids) {
            Evaluation evaluation = new Evaluation();
            evaluation.setId(id);
            evaluation.setIsDeleted(SysConstants.IS_TRUE);
            evaluationList.add(evaluation);
        }
        super.updateBatchById(evaluationList);
    }

    @Override
    public Evaluation findByOrderId(Long orderId) {
        QueryWrapper<Evaluation> ew = new QueryWrapper<>();
        ew.eq(Evaluation.ORDER_ID, orderId);
        ew.eq(Evaluation.IS_DELETED, SysConstants.IS_FALSE);
        ew.orderByDesc(Evaluation.ID);
        ew.last("limit 1");
        return super.getOne(ew);
    }
}
