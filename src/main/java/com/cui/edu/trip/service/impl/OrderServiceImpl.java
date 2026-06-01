package com.cui.edu.trip.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cui.edu.common.HttpStatus;
import com.cui.edu.common.PageResult;
import com.cui.edu.common.PageResultUtil;
import com.cui.edu.common.SysConstants;
import com.cui.edu.config.exception.MyException;
import com.cui.edu.config.redis.DistributedLockHandler;
import com.cui.edu.config.redis.Lock;
import com.cui.edu.system.entity.Museum;
import com.cui.edu.system.service.MuseumService;
import com.cui.edu.trip.entity.ActivityManage;
import com.cui.edu.trip.entity.Order;
import com.cui.edu.trip.entity.OrderDetail;
import com.cui.edu.trip.entity.Team;
import com.cui.edu.trip.entity.Visitor;
import com.cui.edu.trip.mapper.OrderMapper;
import com.cui.edu.trip.service.ActivityManageService;
import com.cui.edu.trip.service.OrderDetailService;
import com.cui.edu.trip.service.OrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cui.edu.trip.service.TeamService;
import com.cui.edu.trip.service.UnionPayService;
import com.cui.edu.trip.service.VisitorService;
import com.cui.edu.util.RedisUtils;
import com.cui.edu.util.TextCodeGenerator;
import com.cui.edu.vo.trip.AppointmentVO;
import com.cui.edu.vo.trip.OrderVO;
import com.cui.edu.vo.trip.VerificationVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * <p>
 * 订单表 服务实现类
 * </p>
 *
 * @author Cuicui
 * @since 2026-05-29
 */
@Service
@Slf4j
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    @Autowired
    private MuseumService museumService;

    @Autowired
    private TextCodeGenerator textCodeGenerator;

    @Autowired
    private UnionPayService unionPayService;

    @Autowired
    private ActivityManageService activityManageService;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private OrderDetailService orderDetailService;

    @Autowired
    private VisitorService visitorService;

    @Autowired
    private TeamService teamService;

    @Autowired
    private DistributedLockHandler distributedLockHandler;

    @Value("${edu.wechat.mini-program.app-id:}")
    private String miniProgramAppId;

    /**
     * 创建待支付订单并获取银联小程序支付参数。
     *
     * <p>下单入口只编排主流程：先做业务校验，再构建主子订单，随后向银联申请支付参数，
     * 最后保存订单并写入 Redis 15 分钟待支付缓存，等待支付回调或超时补偿处理。</p>
     *
     * @param vo      下单参数，支持游客或团队下单，二者存在一个即可
     * @param request 当前请求对象，保留给 Controller 调用链使用
     * @return 成功时返回支付参数和系统订单号，失败时返回 msg 提示
     * @throws Exception 银联支付参数获取异常
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map add(AppointmentVO vo, HttpServletRequest request) throws Exception {
        String checkMsg = checkAppointment(vo);
        if (ObjectUtil.isNotEmpty(checkMsg)) {
            return msgResult(checkMsg);
        }

        Museum museum = museumService.getById(vo.getMuseumId());
        // 构建待支付订单，并估算总金额，创建小程序支付参数
        Order order = buildPayingOrder(vo, countOrderQuantity(vo));
        String params = requestWechatPayParams(order, vo, museum);
        order.setMiniProgramPayParams(params);
        if (ObjectUtil.isEmpty(params)) {
            return msgResult("创建订单失败，支付参数未成功获取");
        }

        // 保存订单，写入待支付缓存
        savePayingOrder(order, vo);
        cachePayingOrder(order);
        return buildAddResult(order);
    }

    /**
     * 统计本次下单的活动票数量。
     *
     * @param vo 下单参数
     * @return 所有活动明细数量之和
     */
    private int countOrderQuantity(AppointmentVO vo) {
        return vo.getList().stream()
                .map(AppointmentVO.AppointmentDetailVO::getNum)
                .mapToInt(Integer::intValue)
                .sum();
    }

    /**
     * 向银联申请微信小程序支付参数。
     *
     * @param order  本地待支付主订单
     * @param vo     下单参数，提供支付金额和 openId
     * @param museum 订单所属博物馆，提供银联商户号和终端号
     * @return 前端拉起微信支付所需的参数
     * @throws Exception 银联接口调用异常
     */
    private String requestWechatPayParams(Order order, AppointmentVO vo, Museum museum) throws Exception {
        // 每个博物馆使用自己的银联商户号和终端号发起小程序支付。
        return unionPayService.wechatAppletPay(order.getOrderNo(), vo.getMoney(),
                museum.getMid(), museum.getTid(), vo.getOpenId(), museum.getName() + "微信-研学", miniProgramAppId);
    }

    /**
     * 保存待支付主订单和对应子订单。
     *
     * <p>主订单先保存是为了拿到数据库主键，后续子订单需要记录 orderId 和 orderNo。</p>
     *
     * @param order 待保存主订单
     * @param vo    下单参数，用于生成子订单
     */
    private void savePayingOrder(Order order, AppointmentVO vo) {
        // 先保存主订单，便于子订单记录orderId。
        super.save(order);
        List<OrderDetail> orderDetailList = buildOrderDetails(order, vo);
        orderDetailService.saveBatch(orderDetailList);
    }

    /**
     * 缓存待支付订单号，触发 15 分钟超时补偿。
     *
     * @param order 待支付主订单
     */
    private void cachePayingOrder(Order order) {
        // 把待支付订单放到redis里，设置15分钟自动过期，过期后查询银联支付状态做补偿处理。
        redisUtils.set(order.getOrderNo(), Order.OrderStatusEnum.PAYING.getValue(), 60 * 15);
    }

    /**
     * 组装下单接口返回值。
     *
     * @param order 已获取支付参数的主订单
     * @return 前端需要的支付参数和系统订单号
     */
    private Map buildAddResult(Order order) {
        Map result = new HashMap(2);
        result.put(Order.MINI_PROGRAM_PAY_PARAMS, order.getMiniProgramPayParams());
        result.put(Order.ORDER_NO, order.getOrderNo());
        return result;
    }

    /**
     * 构建待支付主订单。
     *
     * @param vo            下单参数
     * @param orderQuantity 本次下单票数量
     * @return 未入库的主订单对象
     */
    private Order buildPayingOrder(AppointmentVO vo, int orderQuantity) {
        Order order = new Order();
        order.setPayAmount(vo.getMoney());
        order.setOrderStatus(Order.OrderStatusEnum.PAYING.getValue());
        order.setVisitorId(vo.getVisitorId());
        order.setTeamId(vo.getTeamId());
        order.setOrderType(ObjectUtil.isNotEmpty(vo.getTeamId()) ? 2 : 1);
        order.setIsUsed(SysConstants.IS_FALSE);
        order.setIsDeleted(SysConstants.IS_FALSE);
        order.setMuseumId(vo.getMuseumId());
        order.setOrderQuantity(orderQuantity);
        order.setOrderNo(textCodeGenerator.generate());
        return order;
    }

    /**
     * 根据下单明细生成子订单列表。
     *
     * <p>一个活动购买多个数量时，会拆成多条子订单，方便后续逐个退款和核销统计。</p>
     *
     * @param order 主订单
     * @param vo    下单参数
     * @return 未入库的子订单列表
     */
    private List<OrderDetail> buildOrderDetails(Order order, AppointmentVO vo) {
        List<OrderDetail> orderDetailList = new ArrayList<>();
        for (AppointmentVO.AppointmentDetailVO detailVO : vo.getList()) {
            ActivityManage activityManage = activityManageService.getById(detailVO.getActivityManageId());
            for (int i = 0; i < detailVO.getNum(); i++) {
                orderDetailList.add(buildOrderDetail(order, detailVO, activityManage));
            }
        }
        return orderDetailList;
    }

    /**
     * 构建单条子订单。
     *
     * @param order          主订单
     * @param detailVO       下单活动明细
     * @param activityManage 活动信息，用于确定子订单金额
     * @return 未入库的子订单
     */
    private OrderDetail buildOrderDetail(Order order, AppointmentVO.AppointmentDetailVO detailVO, ActivityManage activityManage) {
        OrderDetail orderDetail = new OrderDetail();
        orderDetail.setOrderId(order.getId());
        orderDetail.setOrderNo(order.getOrderNo());
        orderDetail.setActivityId(detailVO.getActivityManageId());
        orderDetail.setOrderStatus(OrderDetail.OrderDetailStatusEnum.INIT.getValue());
        orderDetail.setMuseumId(order.getMuseumId());
        orderDetail.setOrderAmount(activityManage.getPrice());
        orderDetail.setIsDeleted(SysConstants.IS_FALSE);
        return orderDetail;
    }

    /**
     * 支付成功回调落库逻辑。
     *
     * <p>本方法是支付成功状态变更的唯一入口，银联主动回调和 Redis 超时补偿确认支付成功后都走这里。
     * 这样可以保证主订单、子订单、Redis 待支付缓存的处理口径一致。</p>
     *
     * @param orderNo       系统订单号，银联回调中的 merOrderId
     * @param tradeNo       银联订单号
     * @param requestString 银联原始回调报文或补偿查询结果 JSON，当前服务层仅承接入参
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unionPayNotify(String orderNo, String tradeNo, String requestString) {
        // 第一步：根据系统订单号查询主订单。银联回调里的merOrderId就是本系统订单号。
        Order order = getRequiredOrderForPayNotify(orderNo, tradeNo);

        // 第二步：如果订单已经进入退款链路，说明支付成功状态早已处理过，不能被迟到的支付回调覆盖。
        if (shouldSkipPayNotify(order)) {
            return;
        }

        // 第三步：更新主订单为支付成功，并补充银联订单号；退款字段为空时初始化为0，便于后续退款累计。
        markOrderPaySuccess(order, tradeNo);

        // 第四步：更新子订单状态。只有初始状态的子订单才能被支付回调推进为支付成功。
        markOrderDetailsPaySuccess(order);

        // 第五步：支付已确认，删除15分钟待支付缓存，避免Redis过期补偿再次处理。
        redisUtils.del(order.getOrderNo());
    }

    /**
     * 查询支付回调对应的本地订单。
     *
     * @param orderNo 系统订单号，来自银联 merOrderId
     * @param tradeNo 银联订单号，仅用于异常提示和排查日志
     * @return 本地订单
     */
    private Order getRequiredOrderForPayNotify(String orderNo, String tradeNo) {
        Order order = getOrderByOrderNo(orderNo);
        if (ObjectUtil.isEmpty(order)) {
            throw new MyException(HttpStatus.SC_MY_ERROR, "该银联支付回调时，订单不存在，银联订单：" + tradeNo);
        }
        return order;
    }

    /**
     * 判断支付回调是否需要跳过。
     *
     * <p>支付回调可能重复推送，也可能晚于退款回调到达。订单已支付成功时直接幂等返回；
     * 订单已进入退款链路时不能再被支付回调覆盖为支付成功。</p>
     *
     * @param order 本地订单
     * @return true 表示当前支付回调不再继续处理
     */
    private boolean shouldSkipPayNotify(Order order) {
        if (isRefundOrderStatus(order.getOrderStatus())) {
            log.info("该银联订单已发生退款，不能继续修改为支付成功");
            return true;
        }
        if (Order.OrderStatusEnum.SUCCESS.getValue().equals(order.getOrderStatus())) {
            log.info("该银联订单已支付成功，此次不再处理");
            return true;
        }
        return false;
    }

    /**
     * 将主订单标记为支付成功并补充银联订单号。
     *
     * @param order   本地订单
     * @param tradeNo 银联订单号
     */
    private void markOrderPaySuccess(Order order, String tradeNo) {
        order.setOrderStatus(Order.OrderStatusEnum.SUCCESS.getValue());
        order.setUnionpayOrderNo(tradeNo);
        if (ObjectUtil.isEmpty(order.getRefundAmount())) {
            order.setRefundAmount(0);
        }
        if (ObjectUtil.isEmpty(order.getRefundQuantity())) {
            order.setRefundQuantity(0);
        }
        super.updateById(order);
    }

    /**
     * 将主订单下仍处于初始状态的子订单推进为支付成功。
     *
     * <p>只处理 INIT 状态，避免重复回调覆盖已经退款、退款中或其他人工处理过的子订单。</p>
     *
     * @param order 已支付成功的主订单
     */
    private void markOrderDetailsPaySuccess(Order order) {
        List<OrderDetail> orderDetailList = getOrderDetails(order.getOrderNo());
        for (OrderDetail orderDetail : orderDetailList) {
            if (!OrderDetail.OrderDetailStatusEnum.INIT.getValue().equals(orderDetail.getOrderStatus())) {
                continue;
            }
            orderDetail.setOrderId(order.getId());
            orderDetail.setOrderStatus(OrderDetail.OrderDetailStatusEnum.PAY_SUCCESS.getValue());
        }
        if (!orderDetailList.isEmpty()) {
            orderDetailService.updateBatchById(orderDetailList);
        }
    }

    /**
     * 判断主订单状态是否已经进入退款链路。
     *
     * @param orderStatus 主订单状态
     * @return true 表示订单已全退、部分退款或退款中
     */
    private boolean isRefundOrderStatus(Integer orderStatus) {
        return Order.OrderStatusEnum.ALL_REFUND.getValue().equals(orderStatus)
                || Order.OrderStatusEnum.PARTIAL_REFUND.getValue().equals(orderStatus)
                || Order.OrderStatusEnum.REFUNDING.getValue().equals(orderStatus);
    }

    /**
     * 退款成功回调落库逻辑。
     *
     * <p>退款回调以 refundOrderId 为幂等维度：同一个退款单号只处理一次。
     * 子订单必须先在发起退款时写入 refundId 并置为 REFUNDING，回调成功后这里再改为 REFUND。</p>
     *
     * @param orderNo       系统订单号
     * @param tradeNo       银联订单号，当前仅用于承接回调入参
     * @param money         本次退款金额，单位与银联接口返回保持一致
     * @param refundOrderId 本系统发起退款时生成的退款订单号
     * @param refundTime    银联退款完成时间
     * @param requestString 银联原始回调报文，当前服务层仅承接入参
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unionRefundNotify(String orderNo, String tradeNo, Integer money, String refundOrderId, String refundTime, String requestString) {
        // 第一步：退款回调必须带退款订单号。本系统用refundOrderId定位本次退款涉及的子订单。
        checkRefundNotifyParam(refundOrderId);

        // 第二步：同一个银联退款单号只允许一个回调线程处理，避免并发重复更新退款状态。
        Lock lock = lockRefundNotify(refundOrderId);
        try {
            // 第三步：根据系统订单号查询主订单。主订单不存在说明本地无法承接这笔退款回调。
            Order order = getRequiredOrderForRefundNotify(orderNo);

            // 第四步：根据退款订单号查询本次退款涉及的子订单。
            // 发起退款时应先把这些子订单写入refundId并置为REFUNDING。
            List<OrderDetail> orderDetailList = getRequiredRefundDetails(orderNo, refundOrderId);

            // 第五步：银联可能重复推送退款回调，只要本次退款单里已有子订单完成退款，就认为已处理过并直接返回。
            if (hasRefundedDetail(orderDetailList)) {
                return;
            }

            // 第六步：把本次退款单中仍处于退款中的子订单改为退款成功，并补充退款完成时间、退款金额。
            boolean hasRefunding = markRefundingDetailsRefunded(orderDetailList, money, refundTime);

            // 第七步：如果本次退款单下没有退款中的子订单，说明状态已被其他流程处理，不再改主订单。
            if (!hasRefunding) {
                return;
            }
            orderDetailService.updateBatchById(orderDetailList);

            // 第八步：主订单退款金额和数量以子订单最终状态重新汇总，避免重复回调造成累计偏差。
            refreshOrderRefundInfo(order);
        } finally {
            // 第九步：无论回调处理成功、返回还是异常，都释放本次退款单的分布式锁。
            distributedLockHandler.releaseLock(lock);
        }
    }

    /**
     * 校验退款回调关键参数。
     *
     * @param refundOrderId 退款订单号，用于定位本次退款涉及的子订单
     */
    private void checkRefundNotifyParam(String refundOrderId) {
        if (ObjectUtil.isEmpty(refundOrderId)) {
            throw new MyException(HttpStatus.SC_MY_ERROR, "退款订单号不能为空");
        }
    }

    /**
     * 为退款回调加分布式锁。
     *
     * <p>同一个 refundOrderId 只允许一个线程处理，防止银联重复通知或并发补偿导致重复更新。</p>
     *
     * @param refundOrderId 退款订单号
     * @return 已获取的锁对象，调用方负责释放
     */
    private Lock lockRefundNotify(String refundOrderId) {
        Lock lock = new Lock("unionRefundNotify:" + refundOrderId, UUID.randomUUID().toString());
        boolean locked = distributedLockHandler.tryLock(lock);
        if (!locked) {
            throw new MyException(HttpStatus.SC_MY_ERROR, "退款回调处理中，请勿重复操作");
        }
        return lock;
    }

    /**
     * 查询退款回调对应的主订单。
     *
     * @param orderNo 系统订单号
     * @return 本地主订单
     */
    private Order getRequiredOrderForRefundNotify(String orderNo) {
        Order order = getOrderByOrderNo(orderNo);
        if (ObjectUtil.isEmpty(order)) {
            throw new MyException(HttpStatus.SC_MY_ERROR, "订单不存在，订单id：" + orderNo);
        }
        return order;
    }

    /**
     * 查询本次退款单涉及的子订单。
     *
     * @param orderNo       系统订单号
     * @param refundOrderId 退款订单号
     * @return 本次退款涉及的子订单列表
     */
    private List<OrderDetail> getRequiredRefundDetails(String orderNo, String refundOrderId) {
        List<OrderDetail> orderDetailList = getOrderDetailsByRefundId(orderNo, refundOrderId);
        if (orderDetailList.isEmpty()) {
            throw new MyException(HttpStatus.SC_MY_ERROR, "未查询到退款子订单");
        }
        return orderDetailList;
    }

    /**
     * 判断本次退款单是否已经处理过。
     *
     * <p>只要同一 refundOrderId 下有一条子订单已退款，就认为该退款回调已落库，直接幂等返回。</p>
     *
     * @param orderDetailList 本次退款单涉及的子订单
     * @return true 表示已处理过退款成功回调
     */
    private boolean hasRefundedDetail(List<OrderDetail> orderDetailList) {
        for (OrderDetail orderDetail : orderDetailList) {
            if (OrderDetail.OrderDetailStatusEnum.REFUND.getValue().equals(orderDetail.getOrderStatus())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 将本次退款单下仍处于退款中的子订单标记为退款成功。
     *
     * @param orderDetailList 本次退款单涉及的子订单
     * @param money           银联回调退款金额
     * @param refundTime      银联回调退款完成时间
     * @return true 表示至少有一条退款中子订单被修改
     */
    private boolean markRefundingDetailsRefunded(List<OrderDetail> orderDetailList, Integer money, String refundTime) {
        LocalDateTime refundDateTime = parseRefundTime(refundTime);
        boolean hasRefunding = false;
        for (OrderDetail orderDetail : orderDetailList) {
            if (!OrderDetail.OrderDetailStatusEnum.REFUNDING.getValue().equals(orderDetail.getOrderStatus())) {
                continue;
            }
            hasRefunding = true;
            orderDetail.setOrderStatus(OrderDetail.OrderDetailStatusEnum.REFUND.getValue());
            orderDetail.setRefundTime(refundDateTime);
            orderDetail.setRefundAmount(money);
        }
        return hasRefunding;
    }

    /**
     * Redis 待支付订单 key 过期后的补偿入口。
     *
     * <p>订单超时未收到支付回调时，先查询银联真实支付状态；若银联已支付成功，
     * 则复用支付回调逻辑补齐本地状态，否则将本地待支付订单标记为主动放弃。</p>
     *
     * @param orderNo 系统订单号，也是 Redis 过期 key
     * @throws Exception 银联订单查询接口异常
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handlePayingOrderExpired(String orderNo) throws Exception {
        Order order = getPayingOrderForExpired(orderNo);
        if (ObjectUtil.isEmpty(order)) {
            return;
        }

        Museum museum = getRequiredUnionPayMuseum(order);

        // 15分钟内可能出现银联已支付但回调未到的情况，这里向银联兜底查询一次。
        JSONObject queryResult = unionPayService.appletQuery(orderNo, museum.getMid(), museum.getTid());
        if (ObjectUtil.isEmpty(queryResult)) {
            log.info("订单过期处理：银联查询无结果，订单号：{}", orderNo);
            return;
        }

        if (isUnionPayTradeSuccess(queryResult)) {
            syncPaySuccessFromUnionPayQuery(orderNo, queryResult);
            return;
        }

        log.info("订单过期处理：银联订单未支付成功，订单号：{}，银联状态：{}", orderNo, queryResult.getString("status"));
        abandonPayingOrder(order);
    }

    /**
     * 查询 Redis 过期补偿需要处理的待支付订单。
     *
     * @param orderNo 系统订单号，也是 Redis 过期 key
     * @return 仍处于支付中的订单；不存在或状态已变化时返回 null
     */
    private Order getPayingOrderForExpired(String orderNo) {
        Order order = getOrderByOrderNo(orderNo);
        if (ObjectUtil.isEmpty(order)) {
            log.info("订单过期处理：订单不存在，订单号：{}", orderNo);
            return null;
        }
        // 过期事件只处理仍在支付中的订单，已被回调或人工处理过的订单不再改动。
        if (!Order.OrderStatusEnum.PAYING.getValue().equals(order.getOrderStatus())) {
            log.info("订单过期处理：订单不是支付中状态，不再处理，订单号：{}，状态：{}", orderNo, order.getOrderStatus());
            return null;
        }
        return order;
    }

    /**
     * 查询订单所属博物馆并校验银联配置。
     *
     * @param order 本地订单
     * @return 已配置银联商户号和终端号的博物馆
     */
    private Museum getRequiredUnionPayMuseum(Order order) {
        Museum museum = museumService.getById(order.getMuseumId());
        if (ObjectUtil.isEmpty(museum) || ObjectUtil.isEmpty(museum.getMid()) || ObjectUtil.isEmpty(museum.getTid())) {
            throw new MyException(HttpStatus.SC_MY_ERROR, "订单所属博物馆银联配置不存在，订单号：" + order.getOrderNo());
        }
        return museum;
    }

    /**
     * 判断银联订单查询结果是否为支付成功。
     *
     * @param queryResult 银联查询返回
     * @return true 表示银联确认支付成功
     */
    private boolean isUnionPayTradeSuccess(JSONObject queryResult) {
        return SysConstants.TRADE_SUCCESS.equals(queryResult.getString("status"));
    }

    /**
     * 将银联查询到的支付成功结果同步到本地订单。
     *
     * <p>这里复用支付回调入口，保证补偿查询和真实回调对订单状态的修改完全一致。</p>
     *
     * @param orderNo     系统订单号
     * @param queryResult 银联查询结果
     */
    private void syncPaySuccessFromUnionPayQuery(String orderNo, JSONObject queryResult) {
        String tradeNo = queryResult.getString("targetOrderId");
        // 银联确认支付成功时复用支付回调逻辑，保持订单状态变更口径一致。
        unionPayNotify(orderNo, tradeNo, JSON.toJSONString(queryResult));
    }

    /**
     * 单个子订单申请退款。
     *
     * <p>本方法只负责发起银联退款并把子订单、主订单置为退款中；真正的退款成功状态、
     * 退款时间和主订单退款汇总由银联退款回调统一更新。</p>
     *
     * @param orderDetailId 子订单 ID
     * @param refundReason  申请退款时填写的退款原因
     * @return 空 Map 表示申请成功，带 msg 表示申请失败原因
     * @throws Exception 银联退款接口异常
     */
    @Override
    public Map refund(Long orderDetailId, String refundReason) throws Exception {
        Map result = new HashMap(1);
        OrderDetail orderDetail = orderDetailService.getById(orderDetailId);
        Order order = super.getById(orderDetail.getOrderId());
        String checkMsg = checkSingleRefund(orderDetail, order);
        if (ObjectUtil.isNotEmpty(checkMsg)) {
            result.put(SysConstants.MSG, checkMsg);
            return result;
        }

        Museum museum = museumService.getById(orderDetail.getMuseumId());
        String refundOrderId = textCodeGenerator.generate() + "T";
        // 发起银联退款申请，退款金额为子订单金额
        JSONObject jsonObject = unionPayService.appletRefund(orderDetail.getOrderNo(), order.getUnionpayOrderNo(), orderDetail.getOrderAmount(), museum.getMid(), museum.getTid(), refundOrderId);

        // 校验银联退款申请是否成功
        String refundMsg = checkUnionRefundResponse(jsonObject);
        if (ObjectUtil.isNotEmpty(refundMsg)) {
            result.put(SysConstants.MSG, refundMsg);
            return result;
        }

        // 标记子订单和主订单为退款中状态，并发送退款查询消息
        markOrderDetailRefunding(orderDetail, refundOrderId, refundReason);
        markOrderRefunding(order);
        sendRefundQueryMessage(refundOrderId, order, museum, orderDetail.getRefundAmount());

        // 保存退款单信息
        orderDetailService.updateById(orderDetail);
        super.updateById(order);
        return result;
    }

    /**
     * 主订单一键全额退款申请。
     *
     * <p>一键全退只允许未发生过退款的支付成功订单使用；若已经发生部分退款，
     * 需要走单个子订单退款，避免一次性覆盖已有退款链路。</p>
     *
     * @param orderId      主订单 ID
     * @param refundReason 申请退款时填写的退款原因
     * @return 空 Map 表示申请成功，带 msg 表示申请失败原因
     * @throws Exception 银联退款接口异常
     */
    @Override
    public Map refundAll(String orderId, String refundReason) throws Exception {
        Map<String, String> map = new HashMap(1);
        Order order = super.getById(orderId);
        List<OrderDetail> orderDetails = orderDetailService.findByOrderId(orderId);
        Museum museum = museumService.getById(order.getMuseumId());
        // 校验订单是否已经发生过退款，或者不是支付成功状态
        if (hasRefundQuantity(order) || !Order.OrderStatusEnum.SUCCESS.getValue().equals(order.getOrderStatus())) {
            map.put(SysConstants.MSG, "该订单已有退款操作，无法一键全退，请使用逐个退款功能");
            return map;
        }
        JSONObject query = unionPayService.appletQuery(order.getOrderNo(), museum.getMid(), museum.getTid());
        if (!isUnionSuccessResponse(query)) {
            map.put(SysConstants.MSG, "银联支付查询失败，无法退款");
            return map;
        }

        // 发起银联退款申请，退款金额为订单总支付金额
        Integer totalAmount = query.getInteger("totalAmount");
        String refundOrderId = textCodeGenerator.generate() + "T";
        JSONObject jsonObject = unionPayService.appletRefund(order.getOrderNo(), order.getUnionpayOrderNo(), totalAmount, museum.getMid(), museum.getTid(), refundOrderId);
        // 校验银联退款申请是否成功
        String refundMsg = checkUnionRefundResponse(jsonObject);
        if (ObjectUtil.isNotEmpty(refundMsg)) {
            map.put(SysConstants.MSG, refundMsg);
            return map;
        }

        // 标记子订单和主订单为退款中状态，并发送退款查询消息
        markOrderDetailsRefunding(orderDetails, refundOrderId, refundReason);
        markOrderRefunding(order);
        sendRefundQueryMessage(refundOrderId, order, museum, order.getPayAmount());
        orderDetailService.updateBatchById(orderDetails);
        super.updateById(order);
        return map;
    }

    /**
     * 校验单个子订单是否允许发起退款。
     *
     * @param orderDetail 子订单
     * @param order       主订单
     * @return null 表示校验通过，否则返回前端提示
     */
    private String checkSingleRefund(OrderDetail orderDetail, Order order) {
        if (OrderDetail.OrderDetailStatusEnum.REFUNDING.getValue().equals(orderDetail.getOrderStatus()) ||
                OrderDetail.OrderDetailStatusEnum.REFUND.getValue().equals(orderDetail.getOrderStatus())) {
            return "已操作退款，请勿重复操作";
        }
        if (Order.OrderStatusEnum.PAYING.getValue().equals(order.getOrderStatus()) ||
                Order.OrderStatusEnum.ABANDON.getValue().equals(order.getOrderStatus())) {
            return "订单未完成支付，无法退款";
        }
        return null;
    }

    /**
     * 校验银联退款申请返回结果。
     *
     * @param jsonObject 银联退款申请响应
     * @return null 表示银联已受理退款，否则返回失败提示
     */
    private String checkUnionRefundResponse(JSONObject jsonObject) {
        if (ObjectUtil.isEmpty(jsonObject)) {
            return "退租退款失败，请稍后再试";
        }
        if (SysConstants.POSITION_LACK.equals(jsonObject.getString("errCode"))) {
            return "头寸不足，退款失败";
        }
        if (!SysConstants.SUCCESS.equals(jsonObject.getString("errCode"))) {
            return "退租退款失败，原因：" + jsonObject.getString("errMsg") + "，请稍后再试";
        }
        log.info("退款成功，等待回调");
        return null;
    }

    /**
     * 将子订单标记为退款中。
     *
     * <p>退款原因只在申请退款时写入；退款回调不再覆盖该字段。</p>
     *
     * @param orderDetail   子订单
     * @param refundOrderId 本次退款订单号
     * @param refundReason  申请退款原因
     */
    private void markOrderDetailRefunding(OrderDetail orderDetail, String refundOrderId, String refundReason) {
        orderDetail.setRefundAmount(orderDetail.getOrderAmount());
        orderDetail.setRefundId(refundOrderId);
        orderDetail.setOrderStatus(OrderDetail.OrderDetailStatusEnum.REFUNDING.getValue());
        orderDetail.setRefundReason(refundReason);
    }

    /**
     * 将一批子订单标记为退款中。
     *
     * @param orderDetails  子订单列表
     * @param refundOrderId 本次退款订单号
     * @param refundReason  申请退款原因
     */
    private void markOrderDetailsRefunding(List<OrderDetail> orderDetails, String refundOrderId, String refundReason) {
        for (OrderDetail orderDetail : orderDetails) {
            markOrderDetailRefunding(orderDetail, refundOrderId, refundReason);
        }
    }

    /**
     * 将主订单标记为退款中。
     *
     * @param order 主订单
     */
    private void markOrderRefunding(Order order) {
        order.setOrderStatus(Order.OrderStatusEnum.REFUNDING.getValue());
    }

    /**
     * 判断主订单是否已有退款数量。
     *
     * @param order 主订单
     * @return true 表示已经发生过退款
     */
    private boolean hasRefundQuantity(Order order) {
        return ObjectUtil.isNotEmpty(order.getRefundQuantity()) && order.getRefundQuantity() > 0;
    }

    /**
     * 判断银联接口响应是否成功。
     *
     * @param jsonObject 银联响应
     * @return true 表示响应成功
     */
    private boolean isUnionSuccessResponse(JSONObject jsonObject) {
        return ObjectUtil.isNotEmpty(jsonObject) && SysConstants.SUCCESS.equals(jsonObject.getString("errCode"));
    }

    /**
     * 发送退款查询补偿消息。
     *
     * <p>退款申请成功后，除了等待银联主动回调，也投递一条查询消息用于兜底确认退款结果。</p>
     *
     * @param refundOrderId 退款订单号
     * @param order         主订单
     * @param museum        订单所属博物馆
     * @param money         本次申请退款金额
     */
    private void sendRefundQueryMessage(String refundOrderId, Order order, Museum museum, Integer money) {
        JSONObject json = new JSONObject();
        json.put("refundOrderId", refundOrderId);
        json.put("orderNo", order.getOrderNo());
        json.put("mid", museum.getMid());
        json.put("tid", museum.getTid());
        json.put("money", money);
        redisUtils.convertAndSend("mq_union_refund_query", json);
    }

    /**
     * 主动查询单个子订单退款结果。
     *
     * @param id 子订单 ID
     * @return 查询成功时返回退款成功提示
     * @throws Exception 银联退款查询接口异常
     */
    @Override
    public Map refundQuery(Long id) throws Exception {
        Map map = new HashMap(1);
        OrderDetail orderDetail = orderDetailService.getById(id);
        Museum museum = museumService.getById(orderDetail.getMuseumId());
        JSONObject jsonObject = unionPayService.appletRefundQuery(orderDetail.getRefundId(), museum.getMid(), museum.getTid());
        if (null != jsonObject && (SysConstants.SUCCESS.equals(jsonObject.getString("errCode")))) {
            map.put(SysConstants.MSG, "退款成功，金额：" + jsonObject.getBigDecimal("totalAmount").divide(new BigDecimal(100)) + "元");
        }
        return map;
    }

    /**
     * 放弃仍处于待支付状态的订单。
     *
     * <p>该方法用于 Redis 超时补偿或用户主动放弃支付，只修改仍在 PAYING/INIT 的主子订单，
     * 避免覆盖已经支付成功、退款或被其他流程处理过的数据。</p>
     *
     * @param order 待放弃的主订单
     */
    @Override
    public void abandonPayingOrder(Order order) {
        if (!Order.OrderStatusEnum.PAYING.getValue().equals(order.getOrderStatus())) {
            return;
        }
        order.setOrderStatus(Order.OrderStatusEnum.ABANDON.getValue());
        super.updateById(order);

        List<OrderDetail> orderDetailList = getOrderDetails(order.getOrderNo());
        for (OrderDetail orderDetail : orderDetailList) {
            if (!OrderDetail.OrderDetailStatusEnum.INIT.getValue().equals(orderDetail.getOrderStatus())) {
                continue;
            }
            orderDetail.setOrderStatus(OrderDetail.OrderDetailStatusEnum.ABANDON.getValue());
        }
        if (!orderDetailList.isEmpty()) {
            orderDetailService.updateBatchById(orderDetailList);
        }
    }

    /**
     * 订单核销。
     *
     * <p>核销前会校验订单存在、订单归属博物馆、订单状态和是否已核销；
     * 校验通过后将主订单标记为已使用。</p>
     *
     * @param vo 核销参数，包含订单号和前端当前博物馆 ID
     * @return 空 Map 表示核销成功，带 msg 表示核销失败原因
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map verification(VerificationVO vo) {
        // 第一步：校验核销参数。订单号用于定位订单，博物馆ID用于防止跨馆核销。
        String checkMsg = checkVerificationParam(vo);
        if (ObjectUtil.isNotEmpty(checkMsg)) {
            return msgResult(checkMsg);
        }

        // 第二步：查询订单。这里只查未删除订单，避免已删除订单被继续核销。
        Order order = getOrderByOrderNo(vo.getOrderNo());

        // 第三步：校验订单归属、订单状态和是否已使用。
        checkMsg = checkVerificationOrder(order, vo);
        if (ObjectUtil.isNotEmpty(checkMsg)) {
            return msgResult(checkMsg);
        }

        // 第四步：核销订单。当前订单表只有主订单使用状态，核销时更新主订单为已使用。
        markOrderUsed(order);
        return new HashMap(1);
    }

    /**
     * 根据游客微信 openId 或团队 ID 分页查询订单列表。
     *
     * <p>游客订单通过 openId 先定位游客，再按 visitorId 查询；团队订单直接按 teamId 查询。
     * 如果两个条件都传入，则查询满足任一条件的订单。分页查询只处理当前页订单，并给每个 Order 的 detailList 字段放入子订单集合。</p>
     *
     * @param vo 订单分页查询参数
     * @return 分页订单列表，每条订单的 detailList 字段包含子订单集合
     */
    @Override
    public PageResult findPage(OrderVO vo) {
        Long visitorId = findVisitorIdByWechatOpenid(vo.getOpenId());
        if (ObjectUtil.isEmpty(visitorId) && ObjectUtil.isEmpty(vo.getTeamId())) {
            return emptyOrderPage(vo.getPageNum(), vo.getPageSize());
        }

        Page<Order> page = findOrderPageByVisitorOrTeam(visitorId, vo.getTeamId(), vo.getPageNum(), vo.getPageSize());
        if (page.getRecords().isEmpty()) {
            return PageResultUtil.getPageResult(page);
        }

        fillOrderDetailList(page.getRecords());
        return PageResultUtil.getPageResult(page);
    }

    /**
     * 根据游客微信 openId 查询游客 ID。
     *
     * @param openId 游客微信 openId
     * @return 游客 ID，不存在时返回 null
     */
    private Long findVisitorIdByWechatOpenid(String openId) {
        if (ObjectUtil.isEmpty(openId)) {
            return null;
        }
        QueryWrapper<Visitor> visitorWrapper = new QueryWrapper<>();
        visitorWrapper.eq(Visitor.WECHAT_OPENID, openId);
        visitorWrapper.and(wrapper -> wrapper.eq(Visitor.IS_DELETED, SysConstants.IS_FALSE).or().isNull(Visitor.IS_DELETED));
        visitorWrapper.last("limit 1");
        Visitor visitor = visitorService.getOne(visitorWrapper);
        return ObjectUtil.isEmpty(visitor) ? null : visitor.getId();
    }

    /**
     * 根据游客 ID 或团队 ID 分页查询未删除订单。
     *
     * @param visitorId 游客 ID
     * @param teamId    团队 ID
     * @param pageNum   当前页
     * @param pageSize  每页数量
     * @return 分页订单
     */
    private Page<Order> findOrderPageByVisitorOrTeam(Long visitorId, Long teamId, Integer pageNum, Integer pageSize) {
        Page<Order> page = new Page<>(pageNum, pageSize);
        QueryWrapper<Order> orderWrapper = new QueryWrapper<>();
        orderWrapper.and(wrapper -> wrapper.eq(Order.IS_DELETED, SysConstants.IS_FALSE).or().isNull(Order.IS_DELETED));
        orderWrapper.and(wrapper -> {
            if (ObjectUtil.isNotEmpty(visitorId) && ObjectUtil.isNotEmpty(teamId)) {
                wrapper.eq(Order.VISITOR_ID, visitorId).or().eq(Order.TEAM_ID, teamId);
            } else if (ObjectUtil.isNotEmpty(visitorId)) {
                wrapper.eq(Order.VISITOR_ID, visitorId);
            } else {
                wrapper.eq(Order.TEAM_ID, teamId);
            }
        });
        orderWrapper.orderByDesc(Order.ID);
        return super.page(page, orderWrapper);
    }

    /**
     * 给当前页订单补充子订单集合。
     *
     * <p>这里只查询当前页订单的子订单，避免一次性加载用户全部订单明细。</p>
     *
     * @param orderList 主订单列表
     */
    private void fillOrderDetailList(List<Order> orderList) {
        List<String> orderNoList = new ArrayList<>();
        for (Order order : orderList) {
            orderNoList.add(order.getOrderNo());
        }

        List<OrderDetail> orderDetailList = getOrderDetailsByOrderNoList(orderNoList);
        Map<String, List<OrderDetail>> detailMap = new HashMap<>();
        for (OrderDetail orderDetail : orderDetailList) {
            detailMap.computeIfAbsent(orderDetail.getOrderNo(), key -> new ArrayList<>()).add(orderDetail);
        }

        for (Order order : orderList) {
            // 子订单直接放到 Order 的非数据库字段 detailList 中返回给前端。
            order.setDetailList(detailMap.getOrDefault(order.getOrderNo(), Collections.emptyList()));
        }
    }

    /**
     * 查询指定订单号集合下的未删除子订单。
     *
     * @param orderNoList 当前页订单号集合
     * @return 子订单列表
     */
    private List<OrderDetail> getOrderDetailsByOrderNoList(List<String> orderNoList) {
        QueryWrapper<OrderDetail> detailWrapper = new QueryWrapper<>();
        detailWrapper.in(OrderDetail.ORDER_NO, orderNoList);
        detailWrapper.and(wrapper -> wrapper.eq(OrderDetail.IS_DELETED, SysConstants.IS_FALSE).or().isNull(OrderDetail.IS_DELETED));
        detailWrapper.orderByAsc(OrderDetail.ID);
        return orderDetailService.list(detailWrapper);
    }

    /**
     * 构造空订单分页结果。
     *
     * @param pageNum 当前页
     * @param pageSize 每页数量
     * @return 空分页结果
     */
    private PageResult emptyOrderPage(Integer pageNum, Integer pageSize) {
        Page<Order> page = new Page<>(pageNum, pageSize);
        page.setRecords(Collections.emptyList());
        return PageResultUtil.getPageResult(page);
    }

    /**
     * 校验核销接口必要参数。
     *
     * @param vo 核销参数
     * @return null 表示校验通过，否则返回前端提示
     */
    private String checkVerificationParam(VerificationVO vo) {
        if (ObjectUtil.isEmpty(vo) || ObjectUtil.isEmpty(vo.getOrderNo())) {
            return "订单号不能为空";
        }
        if (ObjectUtil.isEmpty(vo.getMuseumId())) {
            return "博物馆ID不能为空";
        }
        return null;
    }

    /**
     * 校验订单是否允许核销。
     *
     * @param order 本地订单
     * @param vo    核销参数
     * @return null 表示校验通过，否则返回前端提示
     */
    private String checkVerificationOrder(Order order, VerificationVO vo) {
        if (ObjectUtil.isEmpty(order)) {
            return "订单不存在";
        }
        if (ObjectUtil.isEmpty(order.getMuseumId()) || !String.valueOf(order.getMuseumId()).equals(vo.getMuseumId())) {
            return "订单不属于当前博物馆，无法核销";
        }
        if (!Order.OrderStatusEnum.SUCCESS.getValue().equals(order.getOrderStatus())) {
            return "订单状态不允许核销";
        }
        if (SysConstants.IS_TRUE.equals(order.getIsUsed())) {
            return "订单已核销，请勿重复操作";
        }
        return null;
    }

    /**
     * 将订单标记为已核销。
     *
     * @param order 主订单
     */
    private void markOrderUsed(Order order) {
        order.setIsUsed(SysConstants.IS_TRUE);
        super.updateById(order);
    }

    /**
     * 统一组装接口提示信息。
     *
     * @param msg 前端提示
     * @return 只包含 msg 的返回值
     */
    private Map msgResult(String msg) {
        Map result = new HashMap(1);
        result.put(SysConstants.MSG, msg);
        return result;
    }

    /**
     * 根据系统订单号查询未删除主订单。
     *
     * @param orderNo 系统订单号
     * @return 主订单，不存在时返回 null
     */
    private Order getOrderByOrderNo(String orderNo) {
        if (ObjectUtil.isEmpty(orderNo)) {
            return null;
        }
        QueryWrapper<Order> orderWrapper = new QueryWrapper<>();
        orderWrapper.eq(Order.ORDER_NO, orderNo);
        orderWrapper.and(wrapper -> wrapper.eq(Order.IS_DELETED, SysConstants.IS_FALSE).or().isNull(Order.IS_DELETED));
        orderWrapper.last("limit 1");
        return super.getOne(orderWrapper);
    }

    /**
     * 查询主订单下未删除的全部子订单。
     *
     * @param orderNo 系统订单号
     * @return 子订单列表
     */
    private List<OrderDetail> getOrderDetails(String orderNo) {
        QueryWrapper<OrderDetail> detailWrapper = new QueryWrapper<>();
        detailWrapper.eq(OrderDetail.ORDER_NO, orderNo);
        detailWrapper.and(wrapper -> wrapper.eq(OrderDetail.IS_DELETED, SysConstants.IS_FALSE).or().isNull(OrderDetail.IS_DELETED));
        detailWrapper.orderByAsc(OrderDetail.ID);
        return orderDetailService.list(detailWrapper);
    }

    /**
     * 按系统订单号和退款订单号查询未删除子订单。
     *
     * @param orderNo       系统订单号
     * @param refundOrderId 退款订单号
     * @return 本次退款单涉及的子订单列表
     */
    private List<OrderDetail> getOrderDetailsByRefundId(String orderNo, String refundOrderId) {
        QueryWrapper<OrderDetail> detailWrapper = new QueryWrapper<>();
        detailWrapper.eq(OrderDetail.ORDER_NO, orderNo);
        detailWrapper.eq(OrderDetail.REFUND_ID, refundOrderId);
        detailWrapper.and(wrapper -> wrapper.eq(OrderDetail.IS_DELETED, SysConstants.IS_FALSE).or().isNull(OrderDetail.IS_DELETED));
        detailWrapper.orderByAsc(OrderDetail.ID);
        return orderDetailService.list(detailWrapper);
    }

    /**
     * 刷新主订单退款汇总信息。
     *
     * <p>退款回调可能重复推送，也可能一笔订单分多次退款，所以主订单的退款金额和退款数量
     * 不使用“原值 + 本次退款”的方式累计，而是每次从子订单最终状态重新汇总。</p>
     *
     * @param order 主订单
     */
    private void refreshOrderRefundInfo(Order order) {
        // 第一步：查询主订单下全部未删除子订单，作为退款汇总的数据来源。
        List<OrderDetail> orderDetailList = getOrderDetails(order.getOrderNo());
        int refundAmount = 0;
        int refundQuantity = 0;
        boolean hasRefunding = false;

        // 第二步：遍历子订单。已退款子订单参与金额和数量汇总，退款中的子订单用于判断主订单是否仍在退款中。
        for (OrderDetail orderDetail : orderDetailList) {
            if (OrderDetail.OrderDetailStatusEnum.REFUNDING.getValue().equals(orderDetail.getOrderStatus())) {
                hasRefunding = true;
            }
            if (!OrderDetail.OrderDetailStatusEnum.REFUND.getValue().equals(orderDetail.getOrderStatus())) {
                continue;
            }
            refundAmount += ObjectUtil.isEmpty(orderDetail.getRefundAmount()) ? 0 : orderDetail.getRefundAmount();
            refundQuantity++;
        }

        // 第三步：把汇总后的退款金额和退款数量写回主订单。
        order.setRefundAmount(refundAmount);
        order.setRefundQuantity(refundQuantity);

        // 第四步：根据子订单状态和退款汇总结果判断主订单退款状态。
        // 还有子订单退款中：主订单退款中；全部金额或数量已退完：全额退款；否则：部分退款。
        if (hasRefunding) {
            order.setOrderStatus(Order.OrderStatusEnum.REFUNDING.getValue());
        } else if (refundQuantity >= order.getOrderQuantity() || refundAmount >= order.getPayAmount()) {
            order.setOrderStatus(Order.OrderStatusEnum.ALL_REFUND.getValue());
        } else {
            order.setOrderStatus(Order.OrderStatusEnum.PARTIAL_REFUND.getValue());
        }

        // 第五步：保存主订单退款汇总和状态。
        super.updateById(order);
    }

    /**
     * 解析银联退款完成时间。
     *
     * <p>兼容银联常见的 yyyyMMddHHmmss 和 yyyy-MM-dd HH:mm:ss 两种格式；
     * 若为空或无法解析，则使用当前时间兜底，避免退款回调因时间格式异常整体失败。</p>
     *
     * @param refundTime 银联回调时间字符串
     * @return 退款完成时间
     */
    private LocalDateTime parseRefundTime(String refundTime) {
        if (ObjectUtil.isEmpty(refundTime)) {
            return LocalDateTime.now();
        }
        List<DateTimeFormatter> formatters = Arrays.asList(
                DateTimeFormatter.ofPattern("yyyyMMddHHmmss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        );
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDateTime.parse(refundTime, formatter);
            } catch (DateTimeParseException e) {
                // 尝试下一种银联时间格式
            }
        }
        return LocalDateTime.now();
    }

    /**
     * 下单前置业务校验总入口。
     *
     * <p>按参数完整性、游客/团队合法性、博物馆配置、活动金额一致性逐步校验；
     * 任一环节失败直接返回前端提示。</p>
     *
     * @param vo 下单参数
     * @return null 表示校验通过，否则返回前端提示
     */
    private String checkAppointment(AppointmentVO vo) {
        String checkMsg = checkAppointmentParam(vo);
        if (ObjectUtil.isNotEmpty(checkMsg)) {
            return checkMsg;
        }
        checkMsg = checkAppointmentVisitor(vo);
        if (ObjectUtil.isNotEmpty(checkMsg)) {
            return checkMsg;
        }
        checkMsg = checkAppointmentTeam(vo);
        if (ObjectUtil.isNotEmpty(checkMsg)) {
            return checkMsg;
        }
        checkMsg = checkAppointmentMuseum(vo);
        if (ObjectUtil.isNotEmpty(checkMsg)) {
            return checkMsg;
        }
        return checkAppointmentAmount(vo);
    }

    /**
     * 校验下单必要参数。
     *
     * @param vo 下单参数
     * @return null 表示校验通过，否则返回前端提示
     */
    private String checkAppointmentParam(AppointmentVO vo) {
        if (ObjectUtil.isEmpty(vo)) {
            return "参数有误";
        }
        if (ObjectUtil.isEmpty(vo.getVisitorId()) && ObjectUtil.isEmpty(vo.getTeamId())) {
            return "游客ID和团队ID至少填写一个";
        }
        if (ObjectUtil.isEmpty(vo.getMuseumId())) {
            return "博物馆ID不能为空";
        }
        if (ObjectUtil.isEmpty(vo.getOpenId())) {
            return "小程序openId不能为空";
        }
        if (ObjectUtil.isEmpty(vo.getMoney()) || vo.getMoney() <= 0) {
            return "支付金额不能为空且必须大于0";
        }
        if (ObjectUtil.isEmpty(vo.getList())) {
            return "下单详情不能为空";
        }
        return null;
    }

    /**
     * 校验游客是否存在并且没有未支付订单。
     *
     * @param vo 下单参数
     * @return null 表示游客维度校验通过，否则返回前端提示
     */
    private String checkAppointmentVisitor(AppointmentVO vo) {
        if (ObjectUtil.isEmpty(vo.getVisitorId())) {
            return null;
        }
        Visitor visitor = visitorService.getById(vo.getVisitorId());
        if (ObjectUtil.isEmpty(visitor) || SysConstants.IS_TRUE.equals(visitor.getIsDeleted())) {
            return "游客不存在";
        }
        if (hasPayingOrder(Order.VISITOR_ID, vo.getVisitorId())) {
            return "该游客存在未支付订单，请支付或主动放弃后再下单";
        }
        return null;
    }

    /**
     * 校验团队是否存在并且没有未支付订单。
     *
     * @param vo 下单参数
     * @return null 表示团队维度校验通过，否则返回前端提示
     */
    private String checkAppointmentTeam(AppointmentVO vo) {
        if (ObjectUtil.isEmpty(vo.getTeamId())) {
            return null;
        }
        Team team = teamService.getById(vo.getTeamId());
        if (ObjectUtil.isEmpty(team) || SysConstants.IS_TRUE.equals(team.getIsDeleted())) {
            return "团队不存在";
        }
        if (hasPayingOrder(Order.TEAM_ID, vo.getTeamId())) {
            return "该团队存在未支付订单，请支付或主动放弃后再下单";
        }
        return null;
    }

    /**
     * 判断游客或团队是否已有待支付订单。
     *
     * @param column 查询字段，游客使用 visitor_id，团队使用 team_id
     * @param id     游客 ID 或团队 ID
     * @return true 表示存在未支付订单
     */
    private boolean hasPayingOrder(String column, Long id) {
        QueryWrapper<Order> orderWrapper = new QueryWrapper<>();
        orderWrapper.eq(column, id);
        orderWrapper.eq(Order.ORDER_STATUS, Order.OrderStatusEnum.PAYING.getValue());
        orderWrapper.and(wrapper -> wrapper.eq(Order.IS_DELETED, SysConstants.IS_FALSE).or().isNull(Order.IS_DELETED));
        return super.count(orderWrapper) > 0;
    }

    /**
     * 校验下单博物馆是否可用，并确认银联配置完整。
     *
     * @param vo 下单参数
     * @return null 表示博物馆校验通过，否则返回前端提示
     */
    private String checkAppointmentMuseum(AppointmentVO vo) {
        Museum museum = museumService.getById(vo.getMuseumId());
        if (ObjectUtil.isEmpty(museum) || !SysConstants.IS_TRUE.equals(museum.getStatus())) {
            return "博物馆不存在或已禁用";
        }
        if (ObjectUtil.isEmpty(museum.getMid()) || ObjectUtil.isEmpty(museum.getTid())) {
            return "博物馆银联商户配置不完整";
        }
        return null;
    }

    /**
     * 校验前端传入支付金额是否等于数据库活动金额合计。
     *
     * <p>金额以数据库活动价格为准，防止前端金额被篡改或传错。</p>
     *
     * @param vo 下单参数
     * @return null 表示金额校验通过，否则返回前端提示
     */
    private String checkAppointmentAmount(AppointmentVO vo) {
        int totalAmount = 0;
        for (AppointmentVO.AppointmentDetailVO detailVO : vo.getList()) {
            String detailMsg = checkAppointmentDetail(vo, detailVO);
            if (ObjectUtil.isNotEmpty(detailMsg)) {
                return detailMsg;
            }
            ActivityManage activityManage = activityManageService.getById(detailVO.getActivityManageId());
            totalAmount += activityManage.getPrice() * detailVO.getNum();
        }
        if (!vo.getMoney().equals(totalAmount)) {
            return "支付金额与活动总金额不一致";
        }
        return null;
    }

    /**
     * 校验单条活动下单明细。
     *
     * @param vo       下单参数
     * @param detailVO 活动明细
     * @return null 表示明细校验通过，否则返回前端提示
     */
    private String checkAppointmentDetail(AppointmentVO vo, AppointmentVO.AppointmentDetailVO detailVO) {
        if (ObjectUtil.isEmpty(detailVO) || ObjectUtil.isEmpty(detailVO.getActivityManageId())) {
            return "活动ID不能为空";
        }
        if (ObjectUtil.isEmpty(detailVO.getNum()) || detailVO.getNum() <= 0) {
            return "活动数量不能为空且必须大于0";
        }
        ActivityManage activityManage = activityManageService.getById(detailVO.getActivityManageId());
        if (ObjectUtil.isEmpty(activityManage) || SysConstants.IS_TRUE.equals(activityManage.getIsDeleted())) {
            return "活动不存在或已删除";
        }
        if (!SysConstants.IS_TRUE.equals(activityManage.getStatus())) {
            return "活动已禁用";
        }
        if (!vo.getMuseumId().equals(activityManage.getMuseumId())) {
            return "活动不属于当前博物馆";
        }
        if (ObjectUtil.isEmpty(activityManage.getPrice()) || activityManage.getPrice() < 0) {
            return "活动价格配置有误";
        }
        return null;
    }
}
