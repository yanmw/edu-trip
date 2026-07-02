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

    /**
     * 分页过滤查询有效评价列表
     *
     * @param vo 包含分页页码、每页大小及可选的订单 ID 过滤条件的 EvaluationVO 对象
     * @return 分页结果 PageResult
     */
    @Override
    public PageResult findPage(EvaluationVO vo) {
        Page<Evaluation> page = new Page<>(vo.getPageNum(), vo.getPageSize());
        QueryWrapper<Evaluation> ew = new QueryWrapper<>();
        // 1. 精确匹配订单 ID
        if (vo.getOrderId() != null) {
            ew.eq(Evaluation.ORDER_ID, vo.getOrderId());
        }
        // 2. 仅查询未逻辑删除的评价
        ew.eq(Evaluation.IS_DELETED, SysConstants.IS_FALSE);
        ew.orderByDesc(Evaluation.ID);
        page = super.page(page, ew);
        return PageResultUtil.getPageResult(page);
    }

    /**
     * 批量逻辑软删除评价记录
     *
     * @param ids 待逻辑删除的评价主键 ID 集合
     */
    @Override
    public void logicDelete(List<Long> ids) {
        List<Evaluation> evaluationList = new ArrayList<>();
        // 1. 组装逻辑删除字段 is_deleted = 1
        for (Long id : ids) {
            Evaluation evaluation = new Evaluation();
            evaluation.setId(id);
            evaluation.setIsDeleted(SysConstants.IS_TRUE);
            evaluationList.add(evaluation);
        }
        // 2. 批量提交更新
        super.updateBatchById(evaluationList);
    }

    /**
     * 根据订单 ID 检索未删除的最新一条评价详情
     *
     * @param orderId 订单主键 ID
     * @return 匹配的评价实体；若不存在则返回 null
     */
    @Override
    public Evaluation findByOrderId(Long orderId) {
        QueryWrapper<Evaluation> ew = new QueryWrapper<>();
        ew.eq(Evaluation.ORDER_ID, orderId);
        ew.eq(Evaluation.IS_DELETED, SysConstants.IS_FALSE);
        ew.orderByDesc(Evaluation.ID);
        ew.last("limit 1"); // 保证防重，仅取最新的单条数据
        return super.getOne(ew);
    }
}
