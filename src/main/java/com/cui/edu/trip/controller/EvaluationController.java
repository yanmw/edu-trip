package com.cui.edu.trip.controller;


import cn.dev33.satoken.annotation.SaIgnore;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.cui.edu.common.HttpResult;
import com.cui.edu.common.HttpStatus;
import com.cui.edu.common.PageResult;
import com.cui.edu.common.SysConstants;
import com.cui.edu.trip.entity.Evaluation;
import com.cui.edu.trip.service.EvaluationService;
import com.cui.edu.util.Log;
import com.cui.edu.vo.trip.EvaluationVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 评价表 前端控制器
 * </p>
 *
 * @author Cuicui
 * @since 2026-05-29
 */
@RestController
@RequestMapping("/trip/evaluation")
@Api(tags = "评价管理")
public class EvaluationController {

    @Autowired
    private EvaluationService evaluationService;

    /**
     * 新增活动/订单评价
     * <p>
     * 业务流程：
     * 1. 验证入参 record 非空。
     * 2. 对新增的评价记录进行默认初始化状态（如将 is_deleted 默认设为 0）。
     * 3. 调用 EvaluationService.save 写入数据库。
     *
     * @param record 评价实体信息
     * @return 包含评价主键 ID 的 HttpResult
     */
    @PostMapping(value = "/save")
    @ApiOperation(value = "新增评价")
    @SaIgnore
    public HttpResult save(@RequestBody Evaluation record) {
        // 1. 校验实体非空
        if (BeanUtil.isNotEmpty(record)) {
            // 2. 初始化未删除状态
            if (record.getIsDeleted() == null) {
                record.setIsDeleted(SysConstants.IS_FALSE);
            }
            // 3. 执行写入
            evaluationService.save(record);
            return HttpResult.ok(record.getId());
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    /**
     * 批量逻辑删除评价
     *
     * @param records 待删除的评价主键 ID 集合
     * @return 删除状态的 HttpResult 响应
     */
    @PostMapping(value = "/delete")
    @ApiOperation(value = "删除评价")
    @SaIgnore
    @Log(title = "删除评价")
    public HttpResult delete(@RequestBody List<Long> records) {
        // 1. 校验待删除主键非空
        if (ObjectUtil.isNotEmpty(records)) {
            // 2. 调用逻辑删除
            evaluationService.logicDelete(records);
            return HttpResult.ok();
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    /**
     * 分页查询评价列表（仅支持按订单 ID (orderId) 精确过滤）
     *
     * @param vo 包含分页和查询条件（orderId）的 EvaluationVO
     * @return 包含分页数据 (PageResult) 的 HttpResult 响应
     */
    @PostMapping(value = "/findPage")
    @ApiOperation(value = "评价查询-分页")
    public HttpResult findPage(@RequestBody EvaluationVO vo) {
        // 1. 校验查询载荷非空
        if (BeanUtil.isNotEmpty(vo)) {
            // 2. 执行分页过滤
            PageResult pageResult = evaluationService.findPage(vo);
            return HttpResult.ok(pageResult);
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    /**
     * 根据订单 ID 查询关联的单条评价详情
     *
     * @param orderId 订单 ID
     * @return 评价实体，如果无则返回空数据
     */
    @GetMapping(value = "/findByOrderId/{orderId}")
    @ApiOperation(value = "根据订单ID查询评价")
    @SaIgnore
    public HttpResult findByOrderId(@ApiParam(value = "订单ID") @PathVariable Long orderId) {
        // 1. 校验订单 ID 非空
        if (ObjectUtil.isNotEmpty(orderId)) {
            // 2. 获取该订单对应的评价
            Evaluation evaluation = evaluationService.findByOrderId(orderId);
            return HttpResult.ok(evaluation);
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }
}
