package com.cui.edu.trip.service;

import com.alibaba.fastjson2.JSONObject;
import com.cui.edu.common.PageResult;
import com.cui.edu.trip.entity.Order;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cui.edu.vo.trip.AppointmentVO;
import com.cui.edu.vo.trip.OrderVO;
import com.cui.edu.vo.trip.VerificationVO;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * <p>
 * 订单表 服务类
 * </p>
 *
 * @author Cuicui
 * @since 2026-05-29
 */
public interface OrderService extends IService<Order> {

    Map add(AppointmentVO vo, HttpServletRequest request) throws Exception;

    /**
     * 前端支付完成后，主动向银联查询并确认支付结果。
     *
     * <p>银联确认支付成功时会复用支付回调逻辑更新本地订单；未确认成功时只返回查询结果，
     * 不主动放弃订单，避免银联状态短暂延迟导致误处理。</p>
     *
     * @param orderNo 系统订单号
     * @return 支付确认结果
     * @throws Exception 银联查询接口异常
     */
    Map confirmPayResult(String orderNo) throws Exception;

    /**
     * 处理银联支付成功回调。
     *
     * <p>该方法只负责支付成功后的本地订单状态变更，可由银联真实回调调用，
     * 也可由 Redis 待支付订单过期补偿逻辑在确认银联已支付后复用。</p>
     *
     * <p>业务规则：</p>
     * <p>1. 根据系统订单号查询主订单，订单不存在时抛出业务异常。</p>
     * <p>2. 如果订单已经进入退款相关状态，不再修改为支付成功。</p>
     * <p>3. 如果订单已是支付成功，按幂等处理直接返回。</p>
     * <p>4. 将主订单更新为支付成功，补充银联订单号，并把初始状态子订单更新为支付成功。</p>
     *
     * @param orderNo 系统订单号，对应银联回调 merOrderId
     * @param tradeNo 银联订单号，对应银联回调 targetOrderId
     * @param totalAmount 银联回调或查询返回的订单金额
     * @param mid 银联商户号
     * @param tid 银联终端号
     * @param requestString 银联原始回调内容或银联查询结果JSON，用于日志追踪
     */
    String unionPayNotify(String orderNo, String tradeNo, Integer totalAmount, String mid, String tid, String requestString);

    /**
     * 处理银联退款成功回调。
     *
     * <p>退款回调按退款订单号加分布式锁，避免同一笔退款在并发或重复回调下被重复处理。
     * 方法会根据退款订单号找到本次退款涉及的子订单，只把退款中的子订单改为已退款，
     * 然后重新汇总主订单退款金额和退款数量。</p>
     *
     * <p>业务规则：</p>
     * <p>1. refundOrderId 不能为空。</p>
     * <p>2. 主订单不存在时抛出业务异常。</p>
     * <p>3. 本次退款单对应的子订单不存在时抛出业务异常。</p>
     * <p>4. 如果任意子订单已是退款状态，认为银联重复回调，直接返回。</p>
     * <p>5. 子订单仍是退款中时，更新为退款，并补充退款时间和退款金额。</p>
     * <p>6. 主订单退款金额和退款数量以所有已退款子订单重新汇总，避免重复累计。</p>
     *
     * @param orderNo 系统订单号，对应银联回调 merOrderId
     * @param tradeNo 银联订单号，对应银联回调 targetOrderId
     * @param money 本次退款金额，单位分，对应银联回调 refundAmount
     * @param refundOrderId 退款订单号，对应银联回调 refundOrderId，也对应子订单 refund_id
     * @param refundTime 银联退款完成时间，对应银联回调 refundPayTime
     * @param requestString 银联原始退款回调内容，用于日志追踪
     */
    String unionRefundNotify(String orderNo, String tradeNo, Integer money, String refundOrderId, String refundTime, String requestString);

    /**
     * 处理银联退款查询明确失败的结果。
     *
     * @param orderNo 系统订单号
     * @param refundOrderId 退款订单号
     */
    void handleRefundQueryFailed(String orderNo, String refundOrderId);

    /**
     * 记录银联退款查询结果。
     *
     * <p>退款查询可能只是处理中，不一定会修改订单状态，但仍需要进入订单日志用于追踪补偿链路。</p>
     *
     * @param orderNo 系统订单号
     * @param refundOrderId 退款订单号
     * @param money 本次退款金额
     * @param result 银联退款查询响应
     * @param eventSource 事件来源
     * @param remark 备注
     */
    void recordUnionRefundQueryLog(String orderNo, String refundOrderId, Integer money, JSONObject result, String eventSource, String remark);

    void handlePayingOrderExpired(String orderNo) throws Exception;

    Map refund(Long orderDetailId, String refundReason) throws Exception;

    Map refundAll(String orderNo, String refundReason) throws Exception;

    Map refundQuery(Long id) throws Exception;

    void abandonPayingOrder(Order order);

    /**
     * 根据订单号放弃待支付订单。
     *
     * <p>放弃前会先查询银联支付状态，避免用户实际已支付但本地误改为放弃支付。</p>
     *
     * @param orderNo 系统订单号
     * @return 空 Map 表示放弃成功，带 msg 表示失败原因
     * @throws Exception 银联查询接口异常
     */
    Map abandonPayingOrder(String orderNo) throws Exception;

    Map verification(VerificationVO vo);

    /**
     * 根据游客微信 openId 或团队 ID 分页查询订单列表，并附带子订单信息。
     *
     * @param vo 订单分页查询参数
     * @return 分页订单列表，每条订单包含子订单集合及相关表信息
     */
    PageResult findPage(OrderVO vo);

    /**
     * 管理端分页查询所有订单，并附带子订单和关联表信息。
     *
     * @param vo 订单分页查询参数，可按订单号、博物馆、状态、团队、游客等条件筛选
     * @return 分页订单列表，每条订单包含子订单集合及相关表信息
     */
    PageResult findAdminPage(OrderVO vo);

    /**
     * 根据订单编号查询订单详情。
     *
     * @param orderNo 订单编号
     * @return 订单详情，包含子订单集合及相关表信息；不存在时返回 null
     */
    Order findByOrderNo(String orderNo);
}
