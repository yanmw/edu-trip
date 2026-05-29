package com.cui.edu.trip.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.cui.edu.common.HttpStatus;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map add(AppointmentVO vo, HttpServletRequest request) throws Exception {
        Map result = new HashMap(2);
        String checkMsg = checkAppointment(vo);
        if (ObjectUtil.isNotEmpty(checkMsg)) {
            result.put(SysConstants.MSG, checkMsg);
            return result;
        }
        // 计算订单中的数量
        int orderQuantity = vo.getList().stream()
                .map(AppointmentVO.AppointmentDetailVO::getNum)
                .mapToInt(Integer::intValue)
                .sum();
        Museum museum = museumService.getById(vo.getMuseumId());
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
        // 获取订单号。
        String orderNo = textCodeGenerator.generate();
        order.setOrderNo(orderNo);
        // 每个博物馆使用自己的银联商户号和终端号发起小程序支付。
        String params = unionPayService.wechatAppletPay(orderNo, vo.getMoney(),
                museum.getMid(), museum.getTid(), vo.getOpenId(), museum.getName() + "微信-研学", miniProgramAppId);
        order.setMiniProgramPayParams(params);
        if (ObjectUtil.isNotEmpty(params)) {
            result.put(Order.MINI_PROGRAM_PAY_PARAMS, params);
            result.put(Order.ORDER_NO, orderNo);
        } else {
            result.put(SysConstants.MSG, "创建订单失败，支付参数未成功获取");
            return result;
        }
        // 先保存主订单，便于子订单记录orderId。
        super.save(order);
        // 构建子订单信息
        List<OrderDetail> orderDetailList = new ArrayList<>();
        for (AppointmentVO.AppointmentDetailVO detailVO : vo.getList()) {
            ActivityManage activityManage = activityManageService.getById(detailVO.getActivityManageId());
            for (int i = 0; i < detailVO.getNum(); i++) {
                OrderDetail orderDetail = new OrderDetail();
                orderDetail.setOrderId(order.getId());
                orderDetail.setOrderNo(orderNo);
                orderDetail.setActivityId(detailVO.getActivityManageId());
                orderDetail.setOrderStatus(OrderDetail.OrderDetailStatusEnum.INIT.getValue());
                orderDetail.setMuseumId(vo.getMuseumId());
                orderDetail.setOrderAmount(activityManage.getPrice());
                orderDetail.setIsDeleted(SysConstants.IS_FALSE);
                orderDetailList.add(orderDetail);
            }
        }
        // 把待支付订单放到redis里，设置15分钟自动过期，过期后判断订单状态依旧为“支付中”的话，把订单状态修改为：放弃支付；并释放占用的导览器数量
        redisUtils.set(order.getOrderNo(), Order.OrderStatusEnum.PAYING.getValue(), 60 * 15);
        orderDetailService.saveBatch(orderDetailList);
        return result;
    }

    /**
     * 支付成功回调落库逻辑。
     *
     * <p>本方法是支付成功状态变更的唯一入口，银联主动回调和 Redis 超时补偿确认支付成功后都走这里。
     * 这样可以保证主订单、子订单、Redis 待支付缓存的处理口径一致。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unionPayNotify(String orderNo, String tradeNo, String requestString) {
        // 第一步：根据系统订单号查询主订单。银联回调里的merOrderId就是本系统订单号。
        Order order = getOrderByOrderNo(orderNo);
        if (ObjectUtil.isEmpty(order)) {
            throw new MyException(HttpStatus.SC_MY_ERROR, "该银联支付回调时，订单不存在，银联订单：" + tradeNo);
        }

        // 第二步：如果订单已经进入退款链路，说明支付成功状态早已处理过，不能被迟到的支付回调覆盖。
        if (isRefundOrderStatus(order.getOrderStatus())) {
            log.info("该银联订单已发生退款，不能继续修改为支付成功");
            return;
        }

        // 第三步：支付回调可能重复推送，主订单已支付成功时直接幂等返回。
        if (Order.OrderStatusEnum.SUCCESS.getValue().equals(order.getOrderStatus())) {
            log.info("该银联订单已支付成功，此次不再处理");
            return;
        }

        // 第四步：更新主订单为支付成功，并补充银联订单号；退款字段为空时初始化为0，便于后续退款累计。
        order.setOrderStatus(Order.OrderStatusEnum.SUCCESS.getValue());
        order.setUnionpayOrderNo(tradeNo);
        if (ObjectUtil.isEmpty(order.getRefundAmount())) {
            order.setRefundAmount(0);
        }
        if (ObjectUtil.isEmpty(order.getRefundQuantity())) {
            order.setRefundQuantity(0);
        }
        super.updateById(order);

        // 第五步：更新子订单状态。只有初始状态的子订单才能被支付回调推进为支付成功。
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

        // 第六步：支付已确认，删除15分钟待支付缓存，避免Redis过期补偿再次处理。
        redisUtils.del(order.getOrderNo());
    }

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
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unionRefundNotify(String orderNo, String tradeNo, Integer money, String refundOrderId, String refundTime, String requestString) {
        // 第一步：退款回调必须带退款订单号。本系统用refundOrderId定位本次退款涉及的子订单。
        if (ObjectUtil.isEmpty(refundOrderId)) {
            throw new MyException(HttpStatus.SC_MY_ERROR, "退款订单号不能为空");
        }

        // 第二步：同一个银联退款单号只允许一个回调线程处理，避免并发重复更新退款状态。
        Lock lock = new Lock("unionRefundNotify:" + refundOrderId, UUID.randomUUID().toString());
        boolean locked = distributedLockHandler.tryLock(lock);
        if (!locked) {
            throw new MyException(HttpStatus.SC_MY_ERROR, "退款回调处理中，请勿重复操作");
        }
        try {
            // 第三步：根据系统订单号查询主订单。主订单不存在说明本地无法承接这笔退款回调。
            Order order = getOrderByOrderNo(orderNo);
            if (ObjectUtil.isEmpty(order)) {
                throw new MyException(HttpStatus.SC_MY_ERROR, "订单不存在，订单id：" + orderNo);
            }

            // 第四步：根据退款订单号查询本次退款涉及的子订单。
            // 发起退款时应先把这些子订单写入refundId并置为REFUNDING。
            List<OrderDetail> orderDetailList = getOrderDetailsByRefundId(orderNo, refundOrderId);
            if (orderDetailList.isEmpty()) {
                throw new MyException(HttpStatus.SC_MY_ERROR, "未查询到退款子订单");
            }

            // 第五步：银联可能重复推送退款回调，只要本次退款单里已有子订单完成退款，就认为已处理过并直接返回。
            for (OrderDetail orderDetail : orderDetailList) {
                if (OrderDetail.OrderDetailStatusEnum.REFUND.getValue().equals(orderDetail.getOrderStatus())) {
                    return;
                }
            }

            // 第六步：把本次退款单中仍处于退款中的子订单改为退款成功，并补充退款完成时间、退款金额。
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
     * Redis待支付订单key过期后的补偿入口。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handlePayingOrderExpired(String orderNo) throws Exception {
        Order order = getOrderByOrderNo(orderNo);
        if (ObjectUtil.isEmpty(order)) {
            log.info("订单过期处理：订单不存在，订单号：{}", orderNo);
            return;
        }
        // 过期事件只处理仍在支付中的订单，已被回调或人工处理过的订单不再改动。
        if (!Order.OrderStatusEnum.PAYING.getValue().equals(order.getOrderStatus())) {
            log.info("订单过期处理：订单不是支付中状态，不再处理，订单号：{}，状态：{}", orderNo, order.getOrderStatus());
            return;
        }

        Museum museum = museumService.getById(order.getMuseumId());
        if (ObjectUtil.isEmpty(museum) || ObjectUtil.isEmpty(museum.getMid()) || ObjectUtil.isEmpty(museum.getTid())) {
            throw new MyException(HttpStatus.SC_MY_ERROR, "订单所属博物馆银联配置不存在，订单号：" + orderNo);
        }

        // 15分钟内可能出现银联已支付但回调未到的情况，这里向银联兜底查询一次。
        JSONObject queryResult = unionPayService.appletQuery(orderNo, museum.getMid(), museum.getTid());
        if (ObjectUtil.isEmpty(queryResult)) {
            log.info("订单过期处理：银联查询无结果，订单号：{}", orderNo);
            return;
        }

        String tradeStatus = queryResult.getString("status");
        if (SysConstants.TRADE_SUCCESS.equals(tradeStatus)) {
            String tradeNo = queryResult.getString("targetOrderId");
            // 银联确认支付成功时复用支付回调逻辑，保持订单状态变更口径一致。
            unionPayNotify(orderNo, tradeNo, JSON.toJSONString(queryResult));
            return;
        }

        log.info("订单过期处理：银联订单未支付成功，订单号：{}，银联状态：{}", orderNo, tradeStatus);
        abandonPayingOrder(order);
    }

    /**
     * 超时后银联仍未支付成功，本地订单主动放弃支付。
     */
    private void abandonPayingOrder(Order order) {
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

    private List<OrderDetail> getOrderDetails(String orderNo) {
        QueryWrapper<OrderDetail> detailWrapper = new QueryWrapper<>();
        detailWrapper.eq(OrderDetail.ORDER_NO, orderNo);
        detailWrapper.and(wrapper -> wrapper.eq(OrderDetail.IS_DELETED, SysConstants.IS_FALSE).or().isNull(OrderDetail.IS_DELETED));
        detailWrapper.orderByAsc(OrderDetail.ID);
        return orderDetailService.list(detailWrapper);
    }

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

    private String checkAppointment(AppointmentVO vo) {
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

        if (ObjectUtil.isNotEmpty(vo.getVisitorId())) {
            Visitor visitor = visitorService.getById(vo.getVisitorId());
            if (ObjectUtil.isEmpty(visitor) || SysConstants.IS_TRUE.equals(visitor.getIsDeleted())) {
                return "游客不存在";
            }

            QueryWrapper<Order> orderWrapper = new QueryWrapper<>();
            orderWrapper.eq(Order.VISITOR_ID, vo.getVisitorId());
            orderWrapper.eq(Order.ORDER_STATUS, Order.OrderStatusEnum.PAYING.getValue());
            orderWrapper.and(wrapper -> wrapper.eq(Order.IS_DELETED, SysConstants.IS_FALSE).or().isNull(Order.IS_DELETED));
            if (super.count(orderWrapper) > 0) {
                return "该游客存在未支付订单，请支付或主动放弃后再下单";
            }
        }

        if (ObjectUtil.isNotEmpty(vo.getTeamId())) {
            Team team = teamService.getById(vo.getTeamId());
            if (ObjectUtil.isEmpty(team) || SysConstants.IS_TRUE.equals(team.getIsDeleted())) {
                return "团队不存在";
            }

            QueryWrapper<Order> orderWrapper = new QueryWrapper<>();
            orderWrapper.eq(Order.TEAM_ID, vo.getTeamId());
            orderWrapper.eq(Order.ORDER_STATUS, Order.OrderStatusEnum.PAYING.getValue());
            orderWrapper.and(wrapper -> wrapper.eq(Order.IS_DELETED, SysConstants.IS_FALSE).or().isNull(Order.IS_DELETED));
            if (super.count(orderWrapper) > 0) {
                return "该团队存在未支付订单，请支付或主动放弃后再下单";
            }
        }

        Museum museum = museumService.getById(vo.getMuseumId());
        if (ObjectUtil.isEmpty(museum) || !SysConstants.IS_TRUE.equals(museum.getStatus())) {
            return "博物馆不存在或已禁用";
        }
        if (ObjectUtil.isEmpty(museum.getMid()) || ObjectUtil.isEmpty(museum.getTid())) {
            return "博物馆银联商户配置不完整";
        }

        int totalAmount = 0;
        for (AppointmentVO.AppointmentDetailVO detailVO : vo.getList()) {
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
            totalAmount += activityManage.getPrice() * detailVO.getNum();
        }

        if (!vo.getMoney().equals(totalAmount)) {
            return "支付金额与活动总金额不一致";
        }
        return null;
    }
}
