package com.cui.edu.trip.controller;


import cn.dev33.satoken.annotation.SaIgnore;
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
import com.cui.edu.util.Log;
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

    /**
     * 新增订单接口 (下单并获取支付参数)
     * <p>
     * 业务流程：
     * 1. 验证入参 vo 是否非空。
     * 2. 调用 OrderService.add 扣减预约名额、构建订单，并请求银联获取微信支付参数。
     * 3. 校验并处理 Service 返回的结果。若结果 Map 中包含错误信息 (msg)，则返回对应报错；
     *    若无错误，返回支付参数包以供前端拉起支付。
     *
     * @param vo 下单预约参数 (AppointmentVO)
     * @param request 当前请求上下文
     * @return 包含支付参数的 HttpResult 响应或错误信息
     * @throws Exception 银联对接或数据处理异常
     */
    @PostMapping(value = "/add")
    @ApiOperation(value = "添加订单")
    @SaIgnore
    @Log(title = "新增订单")
    public HttpResult add(@RequestBody AppointmentVO vo, HttpServletRequest request) throws Exception {
        // 1. 判断入参非空
        if (BeanUtil.isNotEmpty(vo)) {
            // 2. 调用下单服务，该方法内含分布式名额锁与银联下单逻辑
            Map map = orderService.add(vo, request);
            // 3. 检查业务返回结果是否含有错误提示
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
     *
     * @param vo 支付查询参数包，必填本地订单号 orderNo
     * @return 确认成功或失败的响应
     * @throws Exception 银联接口交互异常
     */
    @PostMapping(value = "/pay/query")
    @ApiOperation(value = "主动查询并确认订单支付结果")
    @SaIgnore
    public HttpResult payQuery(@RequestBody OrderPayQueryVO vo) throws Exception {
        if (ObjectUtil.isEmpty(vo) || ObjectUtil.isEmpty(vo.getOrderNo())) {
            return HttpResult.errorBadRequest();
        }
        Map result = orderService.confirmPayResult(vo.getOrderNo());
        return HttpResult.ok(result);
    }

    /**
     * 管理员退款接口 (针对单个子订单进行退款)
     * <p>
     * 业务流程：
     * 1. 调用 OrderService.refund 发起退款申请，对子订单进行状态和时效校验。
     * 2. 处理服务返回结果。若包含错误提示 msg 则代表校验未通过或银联拒绝，返回错误。
     * 3. 退款请求提交成功后，本地子订单状态推进为“退款中”，等待银联异步退款回调以最终确立“已退款”状态。
     *
     * @param orderDetailId 待退款的子订单 ID
     * @param refundReason 退款原因说明
     * @return 退款申请提交状态的 HttpResult
     * @throws Exception 退款接口调用异常
     */
    @GetMapping(value = "/refund")
    @ApiOperation(value = "管理员退款")
    @AvoidRepeatRequest(intervalTime = 7, msg = "退款操作频繁，请稍后再试")
    @Log(title = "管理员退款")
    public HttpResult refund(@ApiParam(value = "子订单号") @RequestParam Long orderDetailId,
                             @ApiParam(value = "退款原因") @RequestParam String refundReason) throws Exception {
        // 1. 调用单个子订单退款服务
        Map result = orderService.refund(orderDetailId, refundReason);
        // 2. 判断退款申请结果
        if (result.containsKey(SysConstants.MSG)) {
            return HttpResult.error(result.get(SysConstants.MSG).toString());
        } else {
            return HttpResult.ok();
        }
    }

    /**
     * 管理员退全款接口 (针对整个主订单下的所有子订单进行一键退款)
     * <p>
     * 业务流程：
     * 1. 验证主订单号 orderNo 是否非空。
     * 2. 调用 OrderService.refundAll 将该主订单下所有支持退款的子订单发起批量退款。
     * 3. 校验退款结果，存在错误直接反馈前端，否则表示所有退款请求均已成功向银联提交。
     *
     * @param orderNo 主订单编号
     * @param refundReason 退全款的原因
     * @return 批量退款请求提交状态的 HttpResult
     * @throws Exception 银联接口交互异常
     */
    @GetMapping(value = "/refundAll")
    @ApiOperation(value = "管理员退全款")
    @AvoidRepeatRequest(intervalTime = 7, msg = "退款操作频繁，请稍后再试")
    @Log(title = "管理员退全款")
    public HttpResult refundAll(@ApiParam(value = "主订单号") @RequestParam String orderNo,
                                @ApiParam(value = "退款原因") @RequestParam String refundReason) throws Exception {
        // 1. 验证主订单号非空
        if (ObjectUtil.isEmpty(orderNo)) {
            return HttpResult.errorBadRequest();
        }
        // 2. 调用退全款服务
        Map result = orderService.refundAll(orderNo, refundReason);
        // 3. 校验返回结果
        if (result.containsKey(SysConstants.MSG)) {
            return HttpResult.error(result.get(SysConstants.MSG).toString());
        } else {
            return HttpResult.ok();
        }
    }

    /**
     * 退款状态查询接口
     * <p>
     * 向银联后台反查该笔子订单的退款是否成功，用来在回调丢失的情况下，进行人工或被动状态校正。
     *
     * @param id 子订单详情 ID (OrderDetail ID)
     * @return 退款查询结果字符串（如：银联已成功退款、退款处理中等）
     * @throws Exception 银联接口交互异常
     */
    @GetMapping(value = "/refundQuery/{id}")
    @ApiOperation(value = "退款查询", response = String.class)
    public HttpResult refundQuery(@ApiParam(value = "子订单id") @PathVariable Long id) throws Exception {
        // 1. 校验 ID 非空
        if (ObjectUtil.isNotEmpty(id)) {
            // 2. 调用服务层发起对银联的退款反查
            Map map = orderService.refundQuery(id);
            if (map.containsKey(SysConstants.MSG)) {
                return HttpResult.ok(map.get(SysConstants.MSG).toString());
            }
            return HttpResult.error(HttpStatus.SC_MY_ERROR, "未查询到结果");
        } else {
            return HttpResult.errorBadRequest();
        }
    }

    /**
     * 放弃支付接口 (主动取消待支付订单)
     * <p>
     * 用户点击取消支付或退出收银台时调用。
     * 1. 校验主订单号非空。
     * 2. 调用 OrderService.abandonPayingOrder 释放该订单占用的活动名额，回滚预约状态，并废弃本地订单状态。
     *
     * @param orderNo 待放弃支付的主订单号
     * @return 放弃操作结果的 HttpResult
     * @throws Exception 数据变更与缓存清理异常
     */
    @GetMapping(value = "/abandon")
    @ApiOperation(value = "放弃支付")
    @SaIgnore
    @Log(title = "放弃支付")
    public HttpResult abandon(@ApiParam(value = "订单号") @RequestParam String orderNo) throws Exception {
        // 1. 校验订单号
        if (ObjectUtil.isEmpty(orderNo)) {
            return HttpResult.errorBadRequest();
        }
        // 2. 调用放弃支付的业务逻辑（释放名额并废弃订单）
        Map result = orderService.abandonPayingOrder(orderNo);
        if (result.containsKey(SysConstants.MSG)) {
            return HttpResult.error(result.get(SysConstants.MSG).toString());
        }
        return HttpResult.ok();
    }

    /**
     * 订单核销接口
     * <p>
     * 游客到达现场后，由管理员通过扫码或输入券码核销其参观资质。
     * 1. 校验核销对象 VerificationVO 是否非空。
     * 2. 调用 OrderService.verification 进行核销判定（包括订单状态是否有效、活动时间是否相符等）。
     *
     * @param vo 包含核销码、场次信息、核销人员的 VerificationVO
     * @return 核销成功的 HttpResult
     */
    @PostMapping(value = "/verification")
    @ApiOperation(value = "核销", response = JSONObject.class)
    @Log(title = "订单核销")
    public HttpResult verification(@RequestBody VerificationVO vo) {
        // 1. 校验入参非空
        if (BeanUtil.isNotEmpty(vo)) {
            // 2. 调用核销服务
            Map map = orderService.verification(vo);
            if (map.containsKey(SysConstants.MSG)) {
                return HttpResult.error(map.get(SysConstants.MSG).toString());
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
     * 返回的每条主订单都会带 detailList 子订单集合，并补充博物馆、游客、团队、活动、场次等关联信息。</p>
     */
    @PostMapping(value = "/findPage")
    @ApiOperation(value = "根据游客openId或团队ID分页查询订单列表")
    @SaIgnore
    public HttpResult findPage(@RequestBody OrderVO vo) {
        if (ObjectUtil.isEmpty(vo)) {
            return HttpResult.errorBadRequest();
        }
        if (ObjectUtil.isEmpty(vo.getOpenId()) && ObjectUtil.isEmpty(vo.getTeamId())) {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "游客openId和团队ID至少填写一个");
        }
        if (isInvalidPageParam(vo)) {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "分页参数必须大于0");
        }
        PageResult pageResult = orderService.findPage(vo);
        return HttpResult.ok(pageResult);
    }

    /**
     * 管理端分页查询所有订单。
     *
     * <p>该接口不要求 openId 或 teamId，默认分页查询所有未删除订单；
     * 可通过订单号、博物馆、订单状态、订单类型、是否核销、游客ID、团队ID、预约日期做筛选。
     * 返回结构与游客/团队订单列表一致，包含主订单、detailList 子订单及关联表信息。</p>
     */
    @PostMapping(value = "/findAdminPage")
    @ApiOperation(value = "管理端分页查询所有订单")
    public HttpResult findAdminPage(@RequestBody OrderVO vo) {
        if (ObjectUtil.isEmpty(vo)) {
            return HttpResult.errorBadRequest();
        }
        if (isInvalidPageParam(vo)) {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "分页参数必须大于0");
        }
        PageResult pageResult = orderService.findAdminPage(vo);
        return HttpResult.ok(pageResult);
    }

    /**
     * 根据订单编号查询订单详情。
     *
     * <p>返回主订单基础信息、detailList 子订单集合，并补充博物馆、游客、团队、活动和场次信息。
     * 这里只查询未删除订单，避免后台误展示已逻辑删除的数据。</p>
     */
    @GetMapping(value ="/findByOrderNo/{orderNo}")
    @ApiOperation(value = "根据订单编号查询订单详情")
    @SaIgnore
    public HttpResult findByOrderNo(@ApiParam(value = "订单编号") @PathVariable String orderNo) {
        if (ObjectUtil.isEmpty(orderNo)) {
            return HttpResult.errorBadRequest();
        }
        Order order = orderService.findByOrderNo(orderNo);
        if (ObjectUtil.isEmpty(order)) {
            return HttpResult.error(HttpStatus.SC_MY_ERROR, "订单不存在");
        }
        return HttpResult.ok(order);
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
    @SaIgnore
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

    /**
     * 辅助方法：安全获取整型请求参数
     *
     * @param request HTTP 请求
     * @param name 参数名
     * @return 对应的整型值，如果为空则返回 null
     */
    private Integer getIntegerParameter(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        if (ObjectUtil.isEmpty(value)) {
            return null;
        }
        return Integer.valueOf(value);
    }

    /**
     * 辅助方法：判断分页参数是否非法
     *
     * @param vo 查询参数
     * @return 若分页页码或每页大小为空，或小于等于0，则返回 true 表示非法
     */
    private boolean isInvalidPageParam(OrderVO vo) {
        return ObjectUtil.isEmpty(vo.getPageNum()) || ObjectUtil.isEmpty(vo.getPageSize())
                || vo.getPageNum() <= 0 || vo.getPageSize() <= 0;
    }

}
