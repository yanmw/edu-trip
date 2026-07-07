package com.cui.edu.trip.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cui.edu.common.HttpResult;
import com.cui.edu.common.HttpStatus;
import com.cui.edu.trip.entity.Evaluation;
import com.cui.edu.trip.mapper.EvaluationMapper;
import com.cui.edu.trip.service.EvaluationService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cui.edu.common.PageResult;
import com.cui.edu.common.PageResultUtil;
import com.cui.edu.common.SysConstants;
import com.cui.edu.util.DateTimeUtils;
import com.cui.edu.vo.trip.EvaluationVO;
import cn.hutool.core.util.ObjectUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
     * 新增评价
     * <p>
     * 1. 校验 orderId 不能为空。
     * 2. 查询该订单是否已存在未删除的评价，有则返回错误提示。
     * 3. 初始化 is_deleted 默认字段。
     * 4. 写入数据库并返回新评价主键 ID。
     *
     * @param record 评价实体
     * @return HttpResult 成功返回主键 ID，校验不通过时返回错误信息
     */
    @Override
    public HttpResult saveEvaluation(Evaluation record) {
        // 1. 校验订单 ID 非空
        if (ObjectUtil.isEmpty(record.getOrderId())) {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "订单ID不能为空");
        }
        // 2. 一单只评一次：查询该订单是否已存在未删除的评价
        if (findByOrderId(record.getOrderId()) != null) {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "该订单已评价过，每个订单仅允许评价一次");
        }
        // 3. 初始化未删除状态
        if (record.getIsDeleted() == null) {
            record.setIsDeleted(SysConstants.IS_FALSE);
        }
        // 4. 写入数据库
        super.save(record);
        return HttpResult.ok(record.getId());
    }

    /**
     * 分页查询评价列表
     * <p>
     * 通过自定义 SQL 关联 order、order_detail、activity_manage，
     * 将博物馆ID、活动ID、活动名称一并返回给前端。
     * 支持按活动名称（模糊）、提交时间范围、总体评分等级过滤。
     *
     * @param vo 分页及查询条件
     * @return 分页结果 PageResult
     */
    @Override
    public PageResult findPage(EvaluationVO vo) {
        Page<Evaluation> page = new Page<>(vo.getPageNum(), vo.getPageSize());
        // 将前端传入的年月日转换为当天开始 / 结束的 LocalDateTime
        LocalDateTime startDateTime = vo.getCreateTimeStart() != null
                ? DateTimeUtils.getStartDateTime(vo.getCreateTimeStart()) : null;
        LocalDateTime endDateTime = vo.getCreateTimeEnd() != null
                ? DateTimeUtils.getEndDateTime(vo.getCreateTimeEnd()) : null;
        page = baseMapper.findPageWithActivity(page, vo, startDateTime, endDateTime);
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
