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
import com.cui.edu.trip.service.UnionPayService;
import com.cui.edu.util.AvoidRepeatRequest;
import com.cui.edu.vo.trip.AppointmentVO;
import com.cui.edu.vo.trip.OrderPayQueryVO;
import com.cui.edu.vo.trip.OrderVO;
import com.cui.edu.vo.trip.VerificationVO;
import io.swagger.annotations.Api;
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
@Api(tags = "订单管理")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private UnionPayService unionPayService;

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
     * 前端支付完成后主动确认支付结果。
     *
     * <p>前端微信支付成功回调只能说明用户端完成了支付流程，后端仍以银联查询结果为准。
     * 如果银联查询确认支付成功，则复用支付回调逻辑推进本地订单状态。</p>
     */
    @PostMapping(value = "/pay/query")
    @ApiOperation(value = "主动查询并确认订单支付结果")
    public HttpResult payQuery(@RequestBody OrderPayQueryVO vo) throws Exception {
        if (ObjectUtil.isEmpty(vo) || ObjectUtil.isEmpty(vo.getOrderNo())) {
            return HttpResult.errorBadRequest();
        }
        Map result = orderService.confirmPayResult(vo.getOrderNo());
        return HttpResult.ok(result);
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

    @PostMapping(value = "/refundAll")
    @ApiOperation(value = "管理员退全款")
    @AvoidRepeatRequest(intervalTime = 7, msg = "退款操作频繁，请稍后再试")
    public HttpResult refundAll(@ApiParam(value = "主订单号") @RequestParam String orderNo,
                                @ApiParam(value = "退款原因") @RequestParam String refundReason) throws Exception {
        if (ObjectUtil.isEmpty(orderNo)) {
            return HttpResult.errorBadRequest();
        }
        Map result = orderService.refundAll(orderNo, refundReason);
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

    @PostMapping(value = "/abandon")
    @ApiOperation(value = "放弃支付")
    public HttpResult abandon(@ApiParam(value = "订单号") @RequestParam String orderNo) throws Exception {
        if (ObjectUtil.isEmpty(orderNo)) {
            return HttpResult.errorBadRequest();
        }
        Map result = orderService.abandonPayingOrder(orderNo);
        if (result.containsKey(SysConstants.MSG)) {
            return HttpResult.error(result.get(SysConstants.MSG).toString());
        }
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
        if (ObjectUtil.isEmpty(vo.getPageNum()) || ObjectUtil.isEmpty(vo.getPageSize()) || vo.getPageNum() <= 0 || vo.getPageSize() <= 0) {
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
    public String unionPayNotify(HttpServletRequest request) {
        Map<String, String[]> map = request.getParameterMap();
        String requestString = JSON.toJSONString(map);
        try {
            if (!unionPayService.verifyNotifySign(map)) {
                log.warn("银联回调验签失败，回调内容：{}", requestString);
                return "FAILED";
            }

            String status = request.getParameter("status");
            if (SysConstants.TRADE_SUCCESS.equals(status)) {
                log.info("银联支付回调内容：{}", requestString);
                orderService.unionPayNotify(request.getParameter("merOrderId"), request.getParameter("targetOrderId"),
                        getIntegerParameter(request, "totalAmount"), request.getParameter("mid"), request.getParameter("tid"), requestString);
                return "SUCCESS";
            } else if (SysConstants.TRADE_REFUND.equals(status)) {
                log.info("银联退款回调内容：{}", requestString);
                orderService.unionRefundNotify(request.getParameter("merOrderId"), request.getParameter("targetOrderId"),
                        getIntegerParameter(request, "refundAmount"), request.getParameter("refundOrderId"), request.getParameter("refundPayTime"), requestString);
                return "SUCCESS";
            }

            log.info("银联回调状态无需处理，status={}，回调内容：{}", status, requestString);
            return "SUCCESS";
        } catch (Exception e) {
            log.error("银联回调处理失败，回调内容：{}", requestString, e);
            return "FAILED";
        }
    }

    private Integer getIntegerParameter(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        if (ObjectUtil.isEmpty(value)) {
            return null;
        }
        return Integer.valueOf(value);
    }

}
