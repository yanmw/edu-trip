package com.cui.edu.trip.controller;


import cn.hutool.core.bean.BeanUtil;
import com.alibaba.fastjson2.JSON;
import com.cui.edu.common.HttpResult;
import com.cui.edu.common.SysConstants;
import com.cui.edu.trip.service.OrderService;
import com.cui.edu.vo.trip.AppointmentVO;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

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
