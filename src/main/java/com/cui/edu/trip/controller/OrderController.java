package com.cui.edu.trip.controller;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONObject;
import com.alibaba.fastjson2.JSON;
import com.cui.edu.common.HttpResult;
import com.cui.edu.common.HttpStatus;
import com.cui.edu.common.PageResult;
import com.cui.edu.common.SysConstants;
import com.cui.edu.trip.entity.Order;
import com.cui.edu.trip.service.OrderService;
import com.cui.edu.util.AvoidRepeatRequest;
import com.cui.edu.vo.trip.AppointmentVO;
import com.cui.edu.vo.trip.OrderVO;
import com.cui.edu.vo.trip.VerificationVO;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * <p>
 * 订单表 前端控制器
 * </p>
 *
 * @author Cuicui
 * @since 2026-05-29
 */
@RestController
@RequestMapping("/trip/order")
@Slf4j
public class OrderController {

    @Autowired
    private OrderService orderService;

    @PostMapping(value = "/add")
    @ApiOperation(value = "添加订单")
    public HttpResult add(@RequestBody AppointmentVO vo, HttpServletRequest request) throws Exception {
        if (BeanUtil.isNotEmpty(vo)) {
            Map map = orderService.add(vo, request);
            if (map.containsKey(SysConstants.MSG)) {
                return HttpResult.error(map.get(SysConstants.MSG).toString());
            } else {
                return HttpResult.ok(map);
            }
        } else {
            return HttpResult.errorBadRequest();
        }
    }

    @GetMapping(value = "/refund")
    @ApiOperation(value = "管理员退款")
    @AvoidRepeatRequest(intervalTime = 7, msg = "退款操作频繁，请稍后再试")
    public HttpResult refund(@ApiParam(value = "子订单号") @RequestParam Long orderDetailId,
                             @ApiParam(value = "退款原因") @RequestParam String refundReason) throws Exception {
        Map result = orderService.refund(orderDetailId, refundReason);
        if (result.containsKey(SysConstants.MSG)) {
            return HttpResult.error(result.get(SysConstants.MSG).toString());
        } else {
            return HttpResult.ok();
        }
    }

    @GetMapping(value = "/refundAll")
    @ApiOperation(value = "管理员退全款")
    @AvoidRepeatRequest(intervalTime = 7, msg = "退款操作频繁，请稍后再试")
    public HttpResult refundAll(@ApiParam(value = "主订单号") @RequestParam String orderId,
                                @ApiParam(value = "退款原因") @RequestParam String refundReason) throws Exception {
        Map result = orderService.refundAll(orderId, refundReason);
        if (result.containsKey(SysConstants.MSG)) {
            return HttpResult.error(result.get(SysConstants.MSG).toString());
        } else {
            return HttpResult.ok();
        }
    }

    @GetMapping(value = "/refundQuery/{id}")
    @ApiOperation(value = "退款查询", response = String.class)
    public HttpResult refundQuery(@ApiParam(value = "子订单id") @PathVariable Long id) throws Exception {
        if (ObjectUtil.isNotEmpty(id)) {
            Map map = orderService.refundQuery(id);
            if (map.containsKey(SysConstants.MSG)) {
                return HttpResult.ok(map.get(SysConstants.MSG).toString());
            }
            return HttpResult.error(HttpStatus.SC_MY_ERROR, "未查询到结果");
        } else {
            return HttpResult.errorBadRequest();
        }
    }

    @GetMapping(value = "/abandon")
    @ApiOperation(value = "放弃支付")
    public HttpResult abandon(@ApiParam(value = "订单号") @RequestParam String orderId) throws Exception {
        Order order = orderService.getById(orderId);
        orderService.abandonPayingOrder(order);
        return HttpResult.ok();
    }

    @PostMapping(value = "/verification")
    @ApiOperation(value = "核销", response = JSONObject.class)
    public HttpResult verification(@RequestBody VerificationVO vo) {
        if (BeanUtil.isNotEmpty(vo)) {
            Map map = orderService.verification(vo);
            if (map.containsKey(SysConstants.MSG)) {
                return HttpResult.ok(map.get(SysConstants.MSG).toString());
            } else {
                return HttpResult.ok();
            }
        } else {
            return HttpResult.errorBadRequest();
        }
    }

    /**
     * 根据游客微信 openId 或团队 ID 分页查询订单列表。
     *
     * <p>openId 用于查询游客个人订单，teamId 用于查询团队订单；两个参数至少传一个。
     * 返回的每条主订单都会带 detailList 子订单集合。</p>
     */
    @PostMapping(value = "/findPage")
    @ApiOperation(value = "根据游客openId或团队ID分页查询订单列表")
    public HttpResult findPage(@RequestBody OrderVO vo) {
        if (ObjectUtil.isEmpty(vo)) {
            return HttpResult.errorBadRequest();
        }
        if (ObjectUtil.isEmpty(vo.getOpenId()) && ObjectUtil.isEmpty(vo.getTeamId())) {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "游客openId和团队ID至少填写一个");
        }
        if (vo.getPageNum() <= 0 || vo.getPageSize() <= 0) {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "分页参数必须大于0");
        }
        PageResult pageResult = orderService.findPage(vo);
        return HttpResult.ok(pageResult);
    }

    /**
     * 银联回调统一入口。
     *
     * <p>银联支付成功和退款成功都会回调到同一个地址，所以这里根据银联返回的
     * status 字段做分流：</p>
     * <p>1. TRADE_SUCCESS：支付成功回调，更新主订单为支付成功，并更新子订单状态。</p>
     * <p>2. TRADE_REFUND：退款成功回调，更新本次退款单对应的子订单，并汇总主订单退款金额和数量。</p>
     *
     * <p>关键字段说明：</p>
     * <p>merOrderId：系统订单号，也就是本地 order_no。</p>
     * <p>targetOrderId：银联订单号，保存到主订单 unionpay_order_no。</p>
     * <p>refundOrderId：退款订单号，只在退款回调中存在，对应子订单 refund_id。</p>
     * <p>refundAmount：本次退款金额，单位分。</p>
     * <p>refundPayTime：银联退款完成时间。</p>
     */
    @PostMapping(value = "/unionPayNotify")
    @ApiOperation(value = "银联支付/退款回调")
    public HttpResult unionPayNotify(HttpServletRequest request) {
        Map map = request.getParameterMap();
        if (request.getParameter("status").equals(SysConstants.TRADE_SUCCESS)) {
            // 支付回调
            String requestString = JSON.toJSONString(map);
            log.info("银联支付回调内容：" + requestString);
            // 我们的订单号
            String orderNo = request.getParameter("merOrderId");
            // 银联订单号
            String tradeNo = request.getParameter("targetOrderId");
            orderService.unionPayNotify(orderNo, tradeNo, requestString);
            return HttpResult.ok();
        } else if (request.getParameter("status").equals(SysConstants.TRADE_REFUND)) {
            // 退款回调
            String requestString = JSON.toJSONString(map);
            log.info("银联退款回调内容：" + requestString);
            // 我们的订单号
            String orderNo = request.getParameter("merOrderId");
            // 银联订单号
            String tradeNo = request.getParameter("targetOrderId");
            // 当前退款金额
            Integer money = Integer.valueOf(request.getParameter("refundAmount"));
            // 退款订单号
            String refundOrderId = request.getParameter("refundOrderId");
            // 退款时间
            String refundTime = request.getParameter("refundPayTime");
            orderService.unionRefundNotify(orderNo, tradeNo, money, refundOrderId, refundTime, requestString);
            return HttpResult.ok();
        } else {
            return HttpResult.ok();
        }
    }

}
