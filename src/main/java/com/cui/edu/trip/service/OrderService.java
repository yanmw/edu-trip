package com.cui.edu.trip.service;

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
     * @param requestString 银联原始回调内容或银联查询结果JSON，用于日志追踪
     */
    void unionPayNotify(String orderNo, String tradeNo, String requestString);

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
    void unionRefundNotify(String orderNo, String tradeNo, Integer money, String refundOrderId, String refundTime, String requestString);

    void handlePayingOrderExpired(String orderNo) throws Exception;

    Map refund(Long orderDetailId, String refundReason) throws Exception;

    Map refundAll(String orderId, String refundReason) throws Exception;

    Map refundQuery(Long id) throws Exception;

    void abandonPayingOrder(Order order);

    Map verification(VerificationVO vo);

    /**
     * 根据游客微信 openId 或团队 ID 分页查询订单列表，并附带子订单信息。
     *
     * @param vo 订单分页查询参数
     * @return 分页订单列表，每条订单的 detailList 字段包含子订单集合
     */
    PageResult findPage(OrderVO vo);
}
