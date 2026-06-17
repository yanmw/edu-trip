package com.cui.edu.trip.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
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
import com.cui.edu.trip.entity.ActivitySchedule;
import com.cui.edu.trip.entity.Order;
import com.cui.edu.trip.entity.OrderDetail;
import com.cui.edu.trip.entity.OrderLog;
import com.cui.edu.trip.entity.Team;
import com.cui.edu.trip.entity.Visitor;
import com.cui.edu.trip.mapper.OrderMapper;
import com.cui.edu.trip.service.ActivityManageService;
import com.cui.edu.trip.service.ActivityScheduleService;
import com.cui.edu.trip.service.OrderDetailService;
import com.cui.edu.trip.service.OrderLogService;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;

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
    private ActivityScheduleService activityScheduleService;

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

    @Autowired
    private OrderLogService orderLogService;

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
        // 同一预约日期、活动、场次的下单需要串行化，避免并发下单同时通过名额校验。
        List<Lock> appointmentSlotLocks = lockAppointmentSlots(vo);
        if (appointmentSlotLocks == null) {
            return msgResult("当前活动场次正在下单，请稍后重试");
        }
        try {
            // 第一步：下单前先做完整业务校验，避免无效订单进入银联支付流程。
            String checkMsg = checkAppointment(vo);
            if (ObjectUtil.isNotEmpty(checkMsg)) {
                return msgResult(checkMsg);
            }

            // 第二步：根据博物馆配置发起银联下单，银联参数成功返回后才落本地订单。
            Museum museum = museumService.getById(vo.getMuseumId());
            // 构建待支付订单，并估算总金额，创建小程序支付参数
            Order order = buildPayingOrder(vo, countOrderQuantity(vo));
            String params = requestWechatPayParams(order, vo, museum);
            order.setMiniProgramPayParams(params);
            if (ObjectUtil.isEmpty(params)) {
                return msgResult("创建订单失败，支付参数未成功获取");
            }

            // 第三步：保存主子订单，并写入 Redis 待支付缓存，后续靠回调或过期补偿推进状态。
            savePayingOrder(order, vo);
            cachePayingOrder(order);
            saveOrderCreatedLog(order, vo);
            return buildAddResult(order);
        } finally {
            // 无论下单成功、校验失败还是银联异常，都要释放场次锁，避免后续用户无法下单。
            releaseAppointmentSlotLocks(appointmentSlotLocks);
        }
    }

    /**
     * 按预约日期、活动、场次加锁，保证名额校验到订单保存期间同一场次串行处理。
     *
     * @param vo 下单参数
     * @return 锁集合；返回 null 表示获取锁失败
     */
    private List<Lock> lockAppointmentSlots(AppointmentVO vo) {
        // 基础参数不完整时先不加锁，后续参数校验会返回更明确的业务提示。
        if (ObjectUtil.isEmpty(vo) || ObjectUtil.isEmpty(vo.getMuseumId())
                || ObjectUtil.isEmpty(vo.getAppointmentDate()) || ObjectUtil.isEmpty(vo.getList())) {
            return Collections.emptyList();
        }
        // TreeSet 保证多个场次按固定顺序加锁，降低并发下单时互相等待的风险。
        Set<String> lockNames = new TreeSet<>();
        for (AppointmentVO.AppointmentDetailVO detailVO : vo.getList()) {
            // 明细参数缺失时不参与锁构建，后续 checkAppointmentDetail 会统一给出提示。
            if (ObjectUtil.isEmpty(detailVO) || ObjectUtil.isEmpty(detailVO.getActivityManageId())
                    || ObjectUtil.isEmpty(detailVO.getActivityScheduleId())) {
                continue;
            }
            // 锁粒度精确到博物馆、预约日期、活动和场次，同一场次串行，不同场次互不影响。
            lockNames.add("order:add:capacity:" + vo.getMuseumId() + ":" + vo.getAppointmentDate()
                    + ":" + detailVO.getActivityManageId() + ":" + detailVO.getActivityScheduleId());
        }
        List<Lock> locks = new ArrayList<>();
        for (String lockName : lockNames) {
            Lock lock = new Lock(lockName, UUID.randomUUID().toString());
            // 下单链路会访问银联，锁有效期给到 60 秒；获取锁最多等待 5 秒。
            boolean locked = distributedLockHandler.tryLock(lock, 5000L, 50L, 60 * 1000L);
            if (!locked) {
                // 任意一个场次锁获取失败，都释放已获取的锁并让前端稍后重试。
                releaseAppointmentSlotLocks(locks);
                return null;
            }
            locks.add(lock);
        }
        return locks;
    }

    /**
     * 释放本次下单占用的场次锁。
     *
     * @param locks 已获取的锁集合
     */
    private void releaseAppointmentSlotLocks(List<Lock> locks) {
        if (ObjectUtil.isEmpty(locks)) {
            return;
        }
        for (Lock lock : locks) {
            // Redis 锁释放使用 value 校验，防止误删其他请求后续拿到的同名锁。
            distributedLockHandler.releaseLock(lock);
        }
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
        order.setBatchNo(StrUtil.isBlank(vo.getBatchNo()) ? null : vo.getBatchNo().trim());
        order.setOrderType(ObjectUtil.isNotEmpty(vo.getTeamId()) ? 2 : 1);
        order.setIsUsed(SysConstants.IS_FALSE);
        order.setIsDeleted(SysConstants.IS_FALSE);
        order.setAppointmentDate(vo.getAppointmentDate());
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
        orderDetail.setActivityScheduleId(detailVO.getActivityScheduleId());
        orderDetail.setOrderStatus(OrderDetail.OrderDetailStatusEnum.INIT.getValue());
        orderDetail.setMuseumId(order.getMuseumId());
        orderDetail.setOrderAmount(activityManage.getPrice());
        orderDetail.setIsDeleted(SysConstants.IS_FALSE);
        return orderDetail;
    }

    /**
     * 前端支付完成后主动确认订单支付结果。
     *
     * <p>该方法只做“确认支付成功”的补偿：银联查询确认成功时复用支付回调逻辑更新本地订单；
     * 查询为空、未成功或状态延迟时只把结果返回给前端，不在这里放弃支付订单。</p>
     *
     * @param orderNo 系统订单号
     * @return 支付确认结果
     * @throws Exception 银联支付查询接口异常
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map confirmPayResult(String orderNo) throws Exception {
        Order order = getRequiredOrderForPayQuery(orderNo);

        // 本地已经成功的订单直接返回，避免重复查银联。
        if (Order.OrderStatusEnum.SUCCESS.getValue().equals(order.getOrderStatus())) {
            return buildPayQueryResult(order, true, SysConstants.TRADE_SUCCESS, "支付成功");
        }
        // 已进入退款链路说明支付成功早已落库，不再按待支付订单处理。
        if (isRefundOrderStatus(order.getOrderStatus())) {
            return buildPayQueryResult(order, true, null, "订单已进入退款流程");
        }
        // 只允许支付中或已放弃订单做主动确认，已放弃订单用于承接极端迟到支付结果。
        if (!Order.OrderStatusEnum.PAYING.getValue().equals(order.getOrderStatus())
                && !Order.OrderStatusEnum.ABANDON.getValue().equals(order.getOrderStatus())) {
            return buildPayQueryResult(order, false, null, "订单当前状态不允许确认支付");
        }

        // 以银联查询结果为准，查询日志单独记录，方便排查前端确认支付未闭环的问题。
        Museum museum = getRequiredUnionPayMuseum(order);
        JSONObject queryResult = unionPayService.appletQuery(orderNo, museum.getMid(), museum.getTid());
        saveUnionPayQueryLog(order, OrderLog.ACTION_PAY_QUERY, OrderLog.SOURCE_USER,
                buildUnionPayQueryRequest(orderNo, museum), queryResult, null);
        if (ObjectUtil.isEmpty(queryResult)) {
            return buildPayQueryResult(order, false, null, "暂未查询到银联支付结果，请稍后重试");
        }
        if (!isUnionSuccessResponse(queryResult)) {
            log.warn("主动确认支付：银联查询失败，订单号：{}，查询结果：{}", orderNo, queryResult);
            return buildPayQueryResult(order, false, queryResult.getString("status"), "银联查询失败，请稍后重试");
        }

        String unionPayStatus = queryResult.getString("status");
        if (isUnionPayTradeSuccess(queryResult)) {
            String tradeNo = queryResult.getString("targetOrderId");
            // 银联主动查询确认支付成功时，直接复用支付回调逻辑，弥补银联回调未送达的情况。
            unionPayNotify(orderNo, tradeNo, queryResult.getInteger("totalAmount"),
                    queryResult.getString("mid"), queryResult.getString("tid"), JSON.toJSONString(queryResult));
            Order latestOrder = getOrderByOrderNo(orderNo);
            return buildPayQueryResult(latestOrder, true, unionPayStatus, "支付成功");
        }
        if (isUnionPayWaitBuyerPay(queryResult)) {
            return buildPayQueryResult(order, false, unionPayStatus, "银联订单等待支付，请完成支付后再确认");
        }
        if (isUnionPayUnknown(queryResult)) {
            return buildPayQueryResult(order, false, unionPayStatus, "银联订单状态暂不明确，请稍后重试");
        }
        if (isUnionPayTradeClosed(queryResult)) {
            return buildPayQueryResult(order, false, unionPayStatus, "银联订单已关闭");
        }

        return buildPayQueryResult(order, false, unionPayStatus, "银联暂未确认支付成功，请稍后重试");
    }

    /**
     * 查询主动支付确认对应的本地订单。
     *
     * @param orderNo 系统订单号
     * @return 本地订单
     */
    private Order getRequiredOrderForPayQuery(String orderNo) {
        Order order = getOrderByOrderNo(orderNo);
        if (ObjectUtil.isEmpty(order)) {
            throw new MyException(HttpStatus.SC_MY_ERROR, "订单不存在，订单号：" + orderNo);
        }
        return order;
    }

    /**
     * 组装主动支付确认返回值。
     *
     * @param order          本地订单
     * @param paySuccess     是否已确认支付成功
     * @param unionPayStatus 银联查询状态
     * @param msg            前端提示
     * @return 支付确认结果
     */
    private Map buildPayQueryResult(Order order, boolean paySuccess, String unionPayStatus, String msg) {
        Map result = new HashMap(6);
        result.put("paySuccess", paySuccess);
        result.put(Order.ORDER_NO, order.getOrderNo());
        result.put(Order.ORDER_STATUS, order.getOrderStatus());
        result.put(Order.UNIONPAY_ORDER_NO, order.getUnionpayOrderNo());
        result.put("unionPayStatus", unionPayStatus);
        result.put(SysConstants.MSG, msg);
        return result;
    }

    /**
     * 支付成功回调落库逻辑。
     *
     * <p>本方法是支付成功状态变更的唯一入口，银联主动回调和 Redis 超时补偿确认支付成功后都走这里。
     * 这样可以保证主订单、子订单、Redis 待支付缓存的处理口径一致。</p>
     *
     * @param orderNo       系统订单号，银联回调中的 merOrderId
     * @param tradeNo       银联订单号
     * @param totalAmount   银联回调或查询返回的支付金额
     * @param mid           银联商户号
     * @param tid           银联终端号
     * @param requestString 银联原始回调报文或补偿查询结果 JSON，当前服务层仅承接入参
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unionPayNotify(String orderNo, String tradeNo, Integer totalAmount, String mid, String tid, String requestString) {
        Order order = null;
        try {
            // 第一步：根据系统订单号查询主订单。银联回调里的merOrderId就是本系统订单号。
            order = getRequiredOrderForPayNotify(orderNo, tradeNo);
            Integer beforeOrderStatus = order.getOrderStatus();

            // 第二步：校验金额、商户号、终端号，避免伪造或串单回调修改本地订单。
            checkPayNotifyBusiness(order, totalAmount, mid, tid);

            // 第三步：如果订单已经进入退款链路，说明支付成功状态早已处理过，不能被迟到的支付回调覆盖。
            if (shouldSkipPayNotify(order)) {
                savePayNotifySkipLog(order, tradeNo, totalAmount, requestString, beforeOrderStatus);
                return;
            }

            // 第四步：更新主订单为支付成功，并补充银联订单号；退款字段为空时初始化为0，便于后续退款累计。
            markOrderPaySuccess(order, tradeNo);

            // 第五步：更新子订单状态。只有初始状态的子订单才能被支付回调推进为支付成功。
            List<Long> affectedDetailIds = markOrderDetailsPaySuccess(order, beforeOrderStatus);

            // 第六步：支付已确认，删除15分钟待支付缓存，避免Redis过期补偿再次处理。
            redisUtils.del(order.getOrderNo());

            savePayNotifySuccessLog(order, tradeNo, totalAmount, requestString, beforeOrderStatus, affectedDetailIds);
        } catch (RuntimeException e) {
            savePayNotifyExceptionLog(order, orderNo, tradeNo, totalAmount, requestString, e);
            throw e;
        }
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
     * 校验支付回调和主动查询结果是否属于当前本地订单。
     *
     * @param order       本地订单
     * @param totalAmount 银联返回金额
     * @param mid         银联商户号
     * @param tid         银联终端号
     */
    private void checkPayNotifyBusiness(Order order, Integer totalAmount, String mid, String tid) {
        if (ObjectUtil.isNotEmpty(totalAmount) && !totalAmount.equals(order.getPayAmount())) {
            throw new MyException(HttpStatus.SC_MY_ERROR, "银联支付金额与本地订单金额不一致，订单号：" + order.getOrderNo());
        }
        Museum museum = getRequiredUnionPayMuseum(order);
        if (ObjectUtil.isNotEmpty(mid) && !mid.equals(museum.getMid())) {
            throw new MyException(HttpStatus.SC_MY_ERROR, "银联商户号与订单所属博物馆不一致，订单号：" + order.getOrderNo());
        }
        if (ObjectUtil.isNotEmpty(tid) && !tid.equals(museum.getTid())) {
            throw new MyException(HttpStatus.SC_MY_ERROR, "银联终端号与订单所属博物馆不一致，订单号：" + order.getOrderNo());
        }
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
            throw new MyException(HttpStatus.SC_MY_ERROR, "该银联订单已发生退款，不能继续修改为支付成功");
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
    private List<Long> markOrderDetailsPaySuccess(Order order, Integer beforeOrderStatus) {
        List<OrderDetail> orderDetailList = getOrderDetails(order.getOrderNo());
        List<Long> affectedDetailIds = new ArrayList<>();
        for (OrderDetail orderDetail : orderDetailList) {
            if (!canMarkDetailPaySuccess(orderDetail, beforeOrderStatus)) {
                continue;
            }
            orderDetail.setOrderId(order.getId());
            orderDetail.setOrderStatus(OrderDetail.OrderDetailStatusEnum.PAY_SUCCESS.getValue());
            affectedDetailIds.add(orderDetail.getId());
        }
        if (!orderDetailList.isEmpty()) {
            orderDetailService.updateBatchById(orderDetailList);
        }
        return affectedDetailIds;
    }

    /**
     * 判断子订单是否能被支付成功回调推进为支付成功。
     *
     * <p>正常支付中订单的子订单是 INIT；如果本地已放弃但银联随后确认支付成功，
     * 子订单可能是 ABANDON，也需要同步纠正为支付成功。</p>
     *
     * @param orderDetail       子订单
     * @param beforeOrderStatus 主订单变更前状态
     * @return true 表示可以更新为支付成功
     */
    private boolean canMarkDetailPaySuccess(OrderDetail orderDetail, Integer beforeOrderStatus) {
        if (OrderDetail.OrderDetailStatusEnum.INIT.getValue().equals(orderDetail.getOrderStatus())) {
            return true;
        }
        return Order.OrderStatusEnum.ABANDON.getValue().equals(beforeOrderStatus)
                && OrderDetail.OrderDetailStatusEnum.ABANDON.getValue().equals(orderDetail.getOrderStatus());
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
        Lock lock = null;
        Order order = null;
        List<OrderDetail> orderDetailList = Collections.emptyList();
        Integer beforeOrderStatus = null;
        Integer beforeDetailStatus = null;
        Integer beforeRefundAmount = null;
        Integer beforeRefundQuantity = null;
        try {
            // 第一步：退款回调必须带退款订单号。本系统用refundOrderId定位本次退款涉及的子订单。
            checkRefundNotifyParam(refundOrderId);

            // 第二步：同一个银联退款单号只允许一个回调线程处理，避免并发重复更新退款状态。
            lock = lockRefundNotify(refundOrderId);

            // 第三步：根据系统订单号查询主订单。主订单不存在说明本地无法承接这笔退款回调。
            order = getRequiredOrderForRefundNotify(orderNo);
            beforeOrderStatus = order.getOrderStatus();
            beforeRefundAmount = order.getRefundAmount();
            beforeRefundQuantity = order.getRefundQuantity();

            // 第四步：根据退款订单号查询本次退款涉及的子订单。
            // 发起退款时应先把这些子订单写入refundId并置为REFUNDING。
            orderDetailList = getRequiredRefundDetails(orderNo, refundOrderId);
            beforeDetailStatus = orderDetailList.get(0).getOrderStatus();

            // 第五步：银联可能重复推送退款回调，只要本次退款单里已有子订单完成退款，就认为已处理过并直接返回。
            if (hasRefundedDetail(orderDetailList)) {
                saveRefundNotifySkipLog(order, orderDetailList, tradeNo, money, refundOrderId, refundTime,
                        requestString, "该退款单已处理过，本次退款回调幂等返回", beforeOrderStatus, beforeDetailStatus,
                        beforeRefundAmount, beforeRefundQuantity);
                return;
            }

            // 第六步：把本次退款单中仍处于退款中的子订单改为退款成功，并补充退款完成时间、退款金额。
            boolean hasRefunding = markRefundingDetailsRefunded(orderDetailList, money, refundTime);

            // 第七步：如果本次退款单下没有退款中的子订单，说明状态已被其他流程处理，不再改主订单。
            if (!hasRefunding) {
                saveRefundNotifySkipLog(order, orderDetailList, tradeNo, money, refundOrderId, refundTime,
                        requestString, "该退款单下没有退款中的子订单，本次退款回调不修改订单", beforeOrderStatus, beforeDetailStatus,
                        beforeRefundAmount, beforeRefundQuantity);
                return;
            }
            orderDetailService.updateBatchById(orderDetailList);

            // 第八步：主订单退款金额和数量以子订单最终状态重新汇总，避免重复回调造成累计偏差。
            refreshOrderRefundInfo(order);
            saveRefundNotifySuccessLog(order, orderDetailList, tradeNo, money, refundOrderId, refundTime,
                    requestString, beforeOrderStatus, beforeDetailStatus, beforeRefundAmount, beforeRefundQuantity);
        } catch (RuntimeException e) {
            saveRefundNotifyExceptionLog(order, orderNo, tradeNo, money, refundOrderId, refundTime, requestString, e);
            throw e;
        } finally {
            // 第九步：无论回调处理成功、返回还是异常，都释放本次退款单的分布式锁。
            if (lock != null) {
                distributedLockHandler.releaseLock(lock);
            }
        }
    }

    /**
     * 银联退款查询明确失败后，回退本地退款中状态。
     *
     * <p>只有仍处于 REFUNDING 的子订单才回退为支付成功；已经退款成功的子订单不动。
     * 主订单根据剩余子订单退款状态重新确定状态。</p>
     *
     * @param orderNo       系统订单号
     * @param refundOrderId 退款订单号
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleRefundQueryFailed(String orderNo, String refundOrderId) {
        Order order = getOrderByOrderNo(orderNo);
        if (ObjectUtil.isEmpty(order)) {
            log.warn("退款查询失败回退：订单不存在，订单号：{}", orderNo);
            saveRefundRollbackExceptionLog(null, orderNo, refundOrderId, "退款查询失败回退：订单不存在");
            return;
        }
        Integer beforeOrderStatus = order.getOrderStatus();
        Integer beforeRefundAmount = order.getRefundAmount();
        Integer beforeRefundQuantity = order.getRefundQuantity();
        List<OrderDetail> orderDetailList = getOrderDetailsByRefundId(orderNo, refundOrderId);
        if (orderDetailList.isEmpty()) {
            log.warn("退款查询失败回退：未查询到退款子订单，订单号：{}，退款单号：{}", orderNo, refundOrderId);
            saveRefundRollbackSkipLog(order, orderDetailList, refundOrderId, "未查询到退款子订单",
                    beforeOrderStatus, null, beforeRefundAmount, beforeRefundQuantity);
            return;
        }

        boolean hasChanged = false;
        Integer beforeDetailStatus = orderDetailList.get(0).getOrderStatus();
        List<Long> affectedDetailIds = new ArrayList<>();
        for (OrderDetail orderDetail : orderDetailList) {
            if (!OrderDetail.OrderDetailStatusEnum.REFUNDING.getValue().equals(orderDetail.getOrderStatus())) {
                continue;
            }
            hasChanged = true;
            affectedDetailIds.add(orderDetail.getId());
            orderDetail.setOrderStatus(OrderDetail.OrderDetailStatusEnum.PAY_SUCCESS.getValue());
            orderDetail.setRefundAmount(null);
            orderDetail.setRefundTime(null);
            orderDetail.setRefundId(null);
        }
        if (!hasChanged) {
            saveRefundRollbackSkipLog(order, orderDetailList, refundOrderId, "没有退款中的子订单需要回退",
                    beforeOrderStatus, beforeDetailStatus, beforeRefundAmount, beforeRefundQuantity);
            return;
        }
        orderDetailService.updateBatchById(orderDetailList);
        refreshOrderAfterRefundFailed(order);
        saveRefundRollbackSuccessLog(order, affectedDetailIds, refundOrderId, beforeOrderStatus, beforeDetailStatus,
                beforeRefundAmount, beforeRefundQuantity);
    }

    /**
     * 记录银联退款查询结果。
     *
     * <p>退款补偿队列查询到处理中、未知、失败等状态时，即使不修改订单，也会写入日志表，
     * 方便后续追踪退款链路。</p>
     *
     * @param orderNo       系统订单号
     * @param refundOrderId 退款订单号
     * @param money         本次退款金额
     * @param result        银联退款查询响应
     * @param eventSource   事件来源
     * @param remark        备注
     */
    @Override
    public void recordUnionRefundQueryLog(String orderNo, String refundOrderId, Integer money,
                                          JSONObject result, String eventSource, String remark) {
        Order order = getOrderByOrderNo(orderNo);
        List<OrderDetail> orderDetailList = ObjectUtil.isEmpty(order)
                ? Collections.emptyList() : getOrderDetailsByRefundId(orderNo, refundOrderId);
        OrderLog orderLog = ObjectUtil.isEmpty(order)
                ? buildOrderLog(orderNo, OrderLog.LOG_TYPE_REFUND, OrderLog.ACTION_REFUND_QUERY, eventSource)
                : buildOrderLog(order, OrderLog.LOG_TYPE_REFUND, OrderLog.ACTION_REFUND_QUERY, eventSource);
        orderLog.setOrderDetailId(firstDetailId(orderDetailList));
        orderLog.setAffectedDetailIds(joinDetailIds(orderDetailList));
        orderLog.setBeforeOrderStatus(ObjectUtil.isEmpty(order) ? null : order.getOrderStatus());
        orderLog.setAfterOrderStatus(ObjectUtil.isEmpty(order) ? null : order.getOrderStatus());
        if (!orderDetailList.isEmpty()) {
            orderLog.setBeforeDetailStatus(orderDetailList.get(0).getOrderStatus());
            orderLog.setAfterDetailStatus(orderDetailList.get(0).getOrderStatus());
        }
        orderLog.setRefundOrderId(refundOrderId);
        orderLog.setTradeAmount(money);
        fillUnionPayResponse(orderLog, result);
        orderLog.setSuccess(isUnionSuccessResponse(result) ? SysConstants.IS_TRUE : SysConstants.IS_FALSE);
        orderLog.setRemark(remark);
        saveOrderLog(orderLog);
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
            throw new MyException(HttpStatus.SC_MY_ERROR, "订单不存在，订单号：" + orderNo);
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
        checkRefundNotifyAmount(orderDetailList, money);
        LocalDateTime refundDateTime = parseRefundTime(refundTime);
        boolean hasRefunding = false;
        for (OrderDetail orderDetail : orderDetailList) {
            if (!OrderDetail.OrderDetailStatusEnum.REFUNDING.getValue().equals(orderDetail.getOrderStatus())) {
                continue;
            }
            hasRefunding = true;
            orderDetail.setOrderStatus(OrderDetail.OrderDetailStatusEnum.REFUND.getValue());
            orderDetail.setRefundTime(refundDateTime);
            if (ObjectUtil.isEmpty(orderDetail.getRefundAmount())) {
                orderDetail.setRefundAmount(orderDetail.getOrderAmount());
            }
        }
        return hasRefunding;
    }

    /**
     * 校验银联退款回调金额是否等于本次退款单下子订单退款金额合计。
     *
     * <p>银联回调 money 表示本次退款单总金额，不能直接写入每个子订单，否则主订单退款汇总会被重复放大。</p>
     *
     * @param orderDetailList 本次退款单涉及的子订单
     * @param money           银联回调本次退款总金额
     */
    private void checkRefundNotifyAmount(List<OrderDetail> orderDetailList, Integer money) {
        if (ObjectUtil.isEmpty(money)) {
            return;
        }
        int localRefundAmount = 0;
        for (OrderDetail orderDetail : orderDetailList) {
            if (!OrderDetail.OrderDetailStatusEnum.REFUNDING.getValue().equals(orderDetail.getOrderStatus())) {
                continue;
            }
            localRefundAmount += ObjectUtil.isEmpty(orderDetail.getRefundAmount())
                    ? orderDetail.getOrderAmount() : orderDetail.getRefundAmount();
        }
        if (localRefundAmount == 0) {
            return;
        }
        if (localRefundAmount != money) {
            throw new MyException(HttpStatus.SC_MY_ERROR, "银联退款金额与本地退款金额不一致，退款单号："
                    + orderDetailList.get(0).getRefundId());
        }
    }

    /**
     * Redis 待支付订单 key 过期后的补偿入口。
     *
     * <p>订单超时未收到支付回调时，先查询银联真实支付状态；若银联已支付成功，
     * 则复用支付回调逻辑补齐本地状态；若银联确认未支付，则先调用银联关单，
     * 关单成功后再将本地待支付订单标记为放弃。</p>
     *
     * @param orderNo 系统订单号，也是 Redis 过期 key
     * @throws Exception 银联订单查询接口异常
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handlePayingOrderExpired(String orderNo) throws Exception {
        // 只处理仍处于支付中的订单，避免 Redis 迟到事件覆盖已变化的订单。
        Order order = getPayingOrderForExpired(orderNo);
        if (ObjectUtil.isEmpty(order)) {
            return;
        }

        Museum museum = getRequiredUnionPayMuseum(order);

        // 15分钟内可能出现银联已支付但回调未到的情况，这里向银联兜底查询一次。
        JSONObject queryResult = unionPayService.appletQuery(orderNo, museum.getMid(), museum.getTid());
        saveUnionPayQueryLog(order, OrderLog.ACTION_ORDER_EXPIRE, OrderLog.SOURCE_REDIS_EXPIRE,
                buildUnionPayQueryRequest(orderNo, museum), queryResult, null);
        if (ObjectUtil.isEmpty(queryResult)) {
            log.info("订单过期处理：银联查询无结果，订单号：{}", orderNo);
            return;
        }
        if (!isUnionSuccessResponse(queryResult)) {
            log.warn("订单过期处理：银联查询失败，不修改本地订单，订单号：{}，查询结果：{}", orderNo, queryResult);
            return;
        }

        if (isUnionPayTradeSuccess(queryResult)) {
            // 银联确认已支付，说明支付回调可能丢失或延迟，复用支付回调逻辑补齐本地状态。
            syncPaySuccessFromUnionPayQuery(orderNo, queryResult);
            return;
        }

        if (isUnionPayCanAbandon(queryResult)) {
            log.info("订单过期处理：银联订单明确未支付成功，订单号：{}，银联状态：{}", orderNo, queryResult.getString("status"));
            // 本地放弃前必须先关银联订单；关单失败时保持支付中，避免用户后续还能支付。
            if (!closeUnionPayOrderBeforeAbandon(order, museum, queryResult, OrderLog.SOURCE_REDIS_EXPIRE)) {
                log.warn("订单过期处理：银联关单失败，本地订单保持支付中并重新进入超时补偿，订单号：{}", orderNo);
                cachePayingOrder(order);
                return;
            }
            abandonPayingOrder(order);
            return;
        }

        log.info("订单过期处理：银联订单状态暂不明确，不修改本地订单，订单号：{}，银联状态：{}", orderNo, queryResult.getString("status"));
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
     * 判断银联订单查询结果是否仍在等待买家支付。
     *
     * @param queryResult 银联查询返回
     * @return true 表示银联订单仍待支付
     */
    private boolean isUnionPayWaitBuyerPay(JSONObject queryResult) {
        return SysConstants.WAIT_BUYER_PAY.equals(queryResult.getString("status"));
    }

    /**
     * 判断银联订单是否已关闭。
     *
     * @param queryResult 银联查询返回
     * @return true 表示银联订单已关闭
     */
    private boolean isUnionPayTradeClosed(JSONObject queryResult) {
        return ObjectUtil.isNotEmpty(queryResult) && SysConstants.TRADE_CLOSED.equals(queryResult.getString("status"));
    }

    /**
     * 判断银联订单状态是否不明确。
     *
     * @param queryResult 银联查询返回
     * @return true 表示状态不明确
     */
    private boolean isUnionPayUnknown(JSONObject queryResult) {
        return SysConstants.UNKNOWN.equals(queryResult.getString("status"));
    }

    /**
     * 判断待支付订单超时后是否可以本地放弃。
     *
     * @param queryResult 银联查询返回
     * @return true 表示银联已明确未支付成功
     */
    private boolean isUnionPayCanAbandon(JSONObject queryResult) {
        return SysConstants.WAIT_BUYER_PAY.equals(queryResult.getString("status"))
                || SysConstants.TRADE_CLOSED.equals(queryResult.getString("status"))
                || SysConstants.NEW_ORDER.equals(queryResult.getString("status"));
    }

    /**
     * 放弃本地订单前关闭银联未支付订单。
     *
     * <p>如果银联查询结果已经是交易关闭，不需要重复关单；否则必须先调用银联关单接口。
     * 只有关单成功后，后续流程才允许把本地订单改为放弃，避免本地放弃但银联仍可支付。</p>
     *
     * @param order       本地待支付订单
     * @param museum      订单所属博物馆
     * @param queryResult 放弃前银联查询结果
     * @param eventSource 事件来源
     * @return true 表示可以继续本地放弃订单
     */
    private boolean closeUnionPayOrderBeforeAbandon(Order order, Museum museum, JSONObject queryResult, String eventSource) {
        // 银联已经关闭时不用重复调用关单接口，可以直接推进本地放弃状态。
        if (isUnionPayTradeClosed(queryResult)) {
            return true;
        }

        String requestContent = buildUnionPayCloseRequest(order.getOrderNo(), museum);
        try {
            // 银联关单成功后，订单才真正不会被继续支付。
            JSONObject closeResult = unionPayService.appletClose(order.getOrderNo(), museum.getMid(), museum.getTid());
            saveUnionPayCloseLog(order, eventSource, requestContent, closeResult, null);
            return isUnionSuccessResponse(closeResult) || isUnionPayTradeClosed(closeResult);
        } catch (Exception e) {
            // 关单异常时不修改本地状态，让订单保持 PAYING 等待重试。
            saveUnionPayCloseExceptionLog(order, eventSource, requestContent, e);
            log.error("银联关单异常，订单号：{}", order.getOrderNo(), e);
            return false;
        }
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
        unionPayNotify(orderNo, tradeNo, queryResult.getInteger("totalAmount"),
                queryResult.getString("mid"), queryResult.getString("tid"), JSON.toJSONString(queryResult));
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
    @Transactional(rollbackFor = Exception.class)
    public Map refund(Long orderDetailId, String refundReason) throws Exception {
        Map result = new HashMap(1);
        // 第一步：先定位子订单和主订单，退款以子订单为最小处理单位。
        if (ObjectUtil.isEmpty(orderDetailId)) {
            return msgResult("子订单ID不能为空");
        }
        OrderDetail orderDetail = orderDetailService.getById(orderDetailId);
        if (ObjectUtil.isEmpty(orderDetail) || SysConstants.IS_TRUE.equals(orderDetail.getIsDeleted())) {
            return msgResult("子订单不存在");
        }
        Order order = super.getById(orderDetail.getOrderId());
        if (ObjectUtil.isEmpty(order) || SysConstants.IS_TRUE.equals(order.getIsDeleted())) {
            return msgResult("主订单不存在");
        }
        Integer beforeOrderStatus = order.getOrderStatus();
        Integer beforeDetailStatus = orderDetail.getOrderStatus();
        Integer beforeRefundAmount = order.getRefundAmount();
        Integer beforeRefundQuantity = order.getRefundQuantity();
        // 第二步：校验订单核销、主订单状态、子订单状态，失败时只记录日志不请求银联。
        String checkMsg = checkSingleRefund(orderDetail, order);
        if (ObjectUtil.isNotEmpty(checkMsg)) {
            result.put(SysConstants.MSG, checkMsg);
            saveRefundApplyCheckFailLog(order, Collections.singletonList(orderDetail), null, null, checkMsg,
                    beforeOrderStatus, beforeDetailStatus, beforeRefundAmount, beforeRefundQuantity);
            return result;
        }

        Museum museum = museumService.getById(orderDetail.getMuseumId());
        if (ObjectUtil.isEmpty(museum) || ObjectUtil.isEmpty(museum.getMid()) || ObjectUtil.isEmpty(museum.getTid())) {
            String msg = "订单所属博物馆银联配置不存在";
            result.put(SysConstants.MSG, msg);
            saveRefundApplyCheckFailLog(order, Collections.singletonList(orderDetail), null, null, msg,
                    beforeOrderStatus, beforeDetailStatus, beforeRefundAmount, beforeRefundQuantity);
            return result;
        }
        String refundOrderId = textCodeGenerator.generate() + "T";
        // 发起银联退款申请，退款金额为子订单金额
        JSONObject jsonObject = unionPayService.appletRefund(orderDetail.getOrderNo(), order.getUnionpayOrderNo(), orderDetail.getOrderAmount(), museum.getMid(), museum.getTid(), refundOrderId);

        // 校验银联退款申请是否成功
        String refundMsg = checkUnionRefundResponse(jsonObject);
        if (ObjectUtil.isNotEmpty(refundMsg)) {
            result.put(SysConstants.MSG, refundMsg);
            saveRefundApplyCheckFailLog(order, Collections.singletonList(orderDetail), refundOrderId, jsonObject, refundMsg,
                    beforeOrderStatus, beforeDetailStatus, beforeRefundAmount, beforeRefundQuantity);
            return result;
        }

        // 标记子订单和主订单为退款中状态，并发送退款查询消息
        markOrderDetailRefunding(orderDetail, refundOrderId, refundReason);
        markOrderRefunding(order);

        // 第三步：银联受理后再落本地退款中状态，并投递退款查询补偿消息。
        orderDetailService.updateById(orderDetail);
        super.updateById(order);
        sendRefundQueryMessage(refundOrderId, order, museum, orderDetail.getRefundAmount());
        saveRefundApplySuccessLog(order, Collections.singletonList(orderDetail), refundOrderId, orderDetail.getRefundAmount(),
                jsonObject, beforeOrderStatus, beforeDetailStatus, beforeRefundAmount, beforeRefundQuantity);
        return result;
    }

    /**
     * 主订单一键全额退款申请。
     *
     * <p>一键全退只允许未发生过退款的支付成功订单使用；若已经发生部分退款，
     * 需要走单个子订单退款，避免一次性覆盖已有退款链路。</p>
     *
     * @param orderNo      系统订单号
     * @param refundReason 申请退款时填写的退款原因
     * @return 空 Map 表示申请成功，带 msg 表示申请失败原因
     * @throws Exception 银联退款接口异常
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map refundAll(String orderNo, String refundReason) throws Exception {
        Map<String, String> map = new HashMap(1);
        // 第一步：按系统订单号查询主订单，一键全退只接受明确的订单号。
        Order order = getOrderByOrderNo(orderNo);
        if (ObjectUtil.isEmpty(order)) {
            map.put(SysConstants.MSG, "订单不存在");
            return map;
        }
        List<OrderDetail> orderDetails = getOrderDetails(order.getOrderNo());
        Museum museum = museumService.getById(order.getMuseumId());
        Integer beforeOrderStatus = order.getOrderStatus();
        Integer beforeDetailStatus = orderDetails.isEmpty() ? null : orderDetails.get(0).getOrderStatus();
        Integer beforeRefundAmount = order.getRefundAmount();
        Integer beforeRefundQuantity = order.getRefundQuantity();
        // 第二步：一键全退必须覆盖全部子订单，所以子订单列表不能为空。
        if (orderDetails.isEmpty()) {
            map.put(SysConstants.MSG, "未查询到子订单");
            saveRefundApplyCheckFailLog(order, orderDetails, null, null, map.get(SysConstants.MSG),
                    beforeOrderStatus, beforeDetailStatus, beforeRefundAmount, beforeRefundQuantity);
            return map;
        }
        if (SysConstants.IS_TRUE.equals(order.getIsUsed())) {
            map.put(SysConstants.MSG, "订单已核销，无法退款");
            saveRefundApplyCheckFailLog(order, orderDetails, null, null, map.get(SysConstants.MSG),
                    beforeOrderStatus, beforeDetailStatus, beforeRefundAmount, beforeRefundQuantity);
            return map;
        }
        // 校验订单是否已经发生过退款，或者不是支付成功状态
        if (hasRefundQuantity(order) || !Order.OrderStatusEnum.SUCCESS.getValue().equals(order.getOrderStatus())) {
            map.put(SysConstants.MSG, "该订单已有退款操作，无法一键全退，请使用逐个退款功能");
            saveRefundApplyCheckFailLog(order, orderDetails, null, null, map.get(SysConstants.MSG),
                    beforeOrderStatus, beforeDetailStatus, beforeRefundAmount, beforeRefundQuantity);
            return map;
        }
        if (!allDetailsCanRefund(orderDetails)) {
            map.put(SysConstants.MSG, "存在不可退款的子订单，无法一键全退");
            saveRefundApplyCheckFailLog(order, orderDetails, null, null, map.get(SysConstants.MSG),
                    beforeOrderStatus, beforeDetailStatus, beforeRefundAmount, beforeRefundQuantity);
            return map;
        }
        if (ObjectUtil.isEmpty(museum) || ObjectUtil.isEmpty(museum.getMid()) || ObjectUtil.isEmpty(museum.getTid())) {
            map.put(SysConstants.MSG, "订单所属博物馆银联配置不存在");
            saveRefundApplyCheckFailLog(order, orderDetails, null, null, map.get(SysConstants.MSG),
                    beforeOrderStatus, beforeDetailStatus, beforeRefundAmount, beforeRefundQuantity);
            return map;
        }
        // 第三步：退款前主动查银联原支付订单，确认银联侧确实支付成功。
        JSONObject query = unionPayService.appletQuery(order.getOrderNo(), museum.getMid(), museum.getTid());
        saveUnionPayQueryLog(order, OrderLog.ACTION_PAY_QUERY, OrderLog.SOURCE_USER,
                buildUnionPayQueryRequest(order.getOrderNo(), museum), query, "一键全退前校验原支付订单状态");
        if (!isUnionSuccessResponse(query) || !isUnionPayTradeSuccess(query)) {
            map.put(SysConstants.MSG, "银联未确认原订单支付成功，无法退款");
            saveRefundApplyCheckFailLog(order, orderDetails, null, query, map.get(SysConstants.MSG),
                    beforeOrderStatus, beforeDetailStatus, beforeRefundAmount, beforeRefundQuantity);
            return map;
        }

        // 发起银联退款申请，退款金额为订单总支付金额
        Integer totalAmount = query.getInteger("totalAmount");
        // 银联支付金额必须等于本地支付金额，防止串单或金额异常订单被退款。
        if (ObjectUtil.isEmpty(totalAmount) || !totalAmount.equals(order.getPayAmount())) {
            map.put(SysConstants.MSG, "银联支付金额与本地订单金额不一致，无法退款");
            saveRefundApplyCheckFailLog(order, orderDetails, null, query, map.get(SysConstants.MSG),
                    beforeOrderStatus, beforeDetailStatus, beforeRefundAmount, beforeRefundQuantity);
            return map;
        }
        String refundOrderId = textCodeGenerator.generate() + "T";
        JSONObject jsonObject = unionPayService.appletRefund(order.getOrderNo(), order.getUnionpayOrderNo(), totalAmount, museum.getMid(), museum.getTid(), refundOrderId);
        // 校验银联退款申请是否成功
        String refundMsg = checkUnionRefundResponse(jsonObject);
        if (ObjectUtil.isNotEmpty(refundMsg)) {
            map.put(SysConstants.MSG, refundMsg);
            saveRefundApplyCheckFailLog(order, orderDetails, refundOrderId, jsonObject, refundMsg,
                    beforeOrderStatus, beforeDetailStatus, beforeRefundAmount, beforeRefundQuantity);
            return map;
        }

        // 标记子订单和主订单为退款中状态，并发送退款查询消息
        markOrderDetailsRefunding(orderDetails, refundOrderId, refundReason);
        markOrderRefunding(order);
        // 第四步：银联受理全退后，本地全部子订单进入同一个退款单号的退款中状态。
        orderDetailService.updateBatchById(orderDetails);
        super.updateById(order);
        sendRefundQueryMessage(refundOrderId, order, museum, totalAmount);
        saveRefundApplySuccessLog(order, orderDetails, refundOrderId, totalAmount, jsonObject,
                beforeOrderStatus, beforeDetailStatus, beforeRefundAmount, beforeRefundQuantity);
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
        if (SysConstants.IS_TRUE.equals(order.getIsUsed())) {
            return "订单已核销，无法退款";
        }
        if (!Order.OrderStatusEnum.SUCCESS.getValue().equals(order.getOrderStatus())
                && !Order.OrderStatusEnum.PARTIAL_REFUND.getValue().equals(order.getOrderStatus())) {
            return "订单当前状态不允许退款";
        }
        if (OrderDetail.OrderDetailStatusEnum.REFUNDING.getValue().equals(orderDetail.getOrderStatus()) ||
                OrderDetail.OrderDetailStatusEnum.REFUND.getValue().equals(orderDetail.getOrderStatus())) {
            return "已操作退款，请勿重复操作";
        }
        if (!OrderDetail.OrderDetailStatusEnum.PAY_SUCCESS.getValue().equals(orderDetail.getOrderStatus())) {
            return "子订单当前状态不允许退款";
        }
        return null;
    }

    /**
     * 校验一键全退涉及的子订单是否都可以退款。
     *
     * @param orderDetails 主订单下的子订单
     * @return true 表示全部子订单都处于支付成功状态
     */
    private boolean allDetailsCanRefund(List<OrderDetail> orderDetails) {
        for (OrderDetail orderDetail : orderDetails) {
            if (!OrderDetail.OrderDetailStatusEnum.PAY_SUCCESS.getValue().equals(orderDetail.getOrderStatus())) {
                return false;
            }
        }
        return true;
    }

    /**
     * 校验银联退款申请返回结果。
     *
     * @param jsonObject 银联退款申请响应
     * @return null 表示银联已受理退款，否则返回失败提示
     */
    private String checkUnionRefundResponse(JSONObject jsonObject) {
        if (ObjectUtil.isEmpty(jsonObject)) {
            return "退款失败，请稍后再试";
        }
        if (SysConstants.POSITION_LACK.equals(jsonObject.getString("errCode"))) {
            return "头寸不足，退款失败";
        }
        if (!SysConstants.SUCCESS.equals(jsonObject.getString("errCode"))) {
            return "退款失败，原因：" + jsonObject.getString("errMsg") + "，请稍后再试";
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
        try {
            redisUtils.convertAndSend("mq_union_refund_query", json);
        } catch (Exception e) {
            log.error("发送退款查询补偿消息失败，退款单号：{}，消息：{}", refundOrderId, json, e);
        }
    }

    /**
     * 主动查询单个子订单退款结果。
     *
     * @param id 子订单 ID
     * @return 查询成功时返回退款成功提示
     * @throws Exception 银联退款查询接口异常
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map refundQuery(Long id) throws Exception {
        Map map = new HashMap(1);
        // 第一步：定位已发起退款的子订单，未生成 refundId 的订单不允许查退款。
        if (ObjectUtil.isEmpty(id)) {
            return msgResult("子订单ID不能为空");
        }
        OrderDetail orderDetail = orderDetailService.getById(id);
        if (ObjectUtil.isEmpty(orderDetail) || SysConstants.IS_TRUE.equals(orderDetail.getIsDeleted())) {
            return msgResult("子订单不存在");
        }
        if (ObjectUtil.isEmpty(orderDetail.getRefundId())) {
            return msgResult("该子订单未发起退款");
        }
        Order order = super.getById(orderDetail.getOrderId());
        if (ObjectUtil.isEmpty(order) || SysConstants.IS_TRUE.equals(order.getIsDeleted())) {
            return msgResult("主订单不存在");
        }
        Museum museum = museumService.getById(orderDetail.getMuseumId());
        if (ObjectUtil.isEmpty(museum) || ObjectUtil.isEmpty(museum.getMid()) || ObjectUtil.isEmpty(museum.getTid())) {
            return msgResult("订单所属博物馆银联配置不存在");
        }
        // 第二步：主动查询银联退款状态，查询本身也写入订单日志。
        JSONObject jsonObject = unionPayService.appletRefundQuery(orderDetail.getRefundId(), museum.getMid(), museum.getTid());
        saveRefundQueryLog(order, orderDetail, jsonObject);
        if (ObjectUtil.isEmpty(jsonObject)) {
            map.put(SysConstants.MSG, "未查询到退款结果");
            return map;
        }
        if (!SysConstants.SUCCESS.equals(jsonObject.getString("errCode"))) {
            map.put(SysConstants.MSG, "退款查询失败，原因：" + jsonObject.getString("errMsg"));
            return map;
        }
        String refundStatus = jsonObject.getString("refundStatus");
        if (SysConstants.REFUND_SUCCESS.equals(refundStatus)) {
            // 银联确认退款成功时，复用退款回调逻辑刷新主子订单状态。
            Integer refundAmount = getUnionRefundAmount(jsonObject, orderDetail);
            String refundTime = ObjectUtil.isNotEmpty(jsonObject.getString("refundPayTime"))
                    ? jsonObject.getString("refundPayTime") : jsonObject.getString("responseTimestamp");
            unionRefundNotify(orderDetail.getOrderNo(), jsonObject.getString("targetOrderId"),
                    refundAmount, orderDetail.getRefundId(), refundTime, jsonObject.toString());
            map.put(SysConstants.MSG, "退款成功，金额：" + getRefundQueryDisplayAmount(jsonObject, orderDetail) + "元");
        } else if (SysConstants.REFUND_PROCESSING.equals(refundStatus) || SysConstants.UNKNOWN.equals(refundStatus)) {
            map.put(SysConstants.MSG, "退款处理中，请稍后再查询");
        } else if (SysConstants.REFUND_FAIL.equals(refundStatus)) {
            // 银联明确退款失败时，把本地退款中子订单回退为支付成功。
            handleRefundQueryFailed(orderDetail.getOrderNo(), orderDetail.getRefundId());
            map.put(SysConstants.MSG, "退款失败，已回退本地退款状态");
        } else {
            map.put(SysConstants.MSG, "退款状态未知：" + refundStatus);
        }
        return map;
    }

    /**
     * 获取退款查询结果中用于展示的退款金额。
     *
     * @param jsonObject  银联退款查询响应
     * @param orderDetail 本地子订单
     * @return 元为单位的退款金额
     */
    private BigDecimal getRefundQueryDisplayAmount(JSONObject jsonObject, OrderDetail orderDetail) {
        BigDecimal amount = jsonObject.getBigDecimal("totalAmount");
        if (ObjectUtil.isEmpty(amount)) {
            amount = jsonObject.getBigDecimal("refundAmount");
        }
        if (ObjectUtil.isEmpty(amount)) {
            amount = new BigDecimal(ObjectUtil.isEmpty(orderDetail.getRefundAmount()) ? 0 : orderDetail.getRefundAmount());
        }
        return amount.divide(new BigDecimal(100));
    }

    /**
     * 获取银联退款结果中的本次退款总金额。
     *
     * <p>如果银联响应未带金额，则按同一 refundId 下本地子订单退款金额合计兜底，
     * 保证手动查询同步退款成功时不会把单个子订单金额误当作整笔退款金额。</p>
     *
     * @param jsonObject  银联退款查询响应
     * @param orderDetail 当前查询的子订单
     * @return 本次退款总金额，单位分
     */
    private Integer getUnionRefundAmount(JSONObject jsonObject, OrderDetail orderDetail) {
        Integer amount = jsonObject.getInteger("refundAmount");
        if (ObjectUtil.isEmpty(amount)) {
            amount = jsonObject.getInteger("totalAmount");
        }
        if (ObjectUtil.isNotEmpty(amount)) {
            return amount;
        }
        int localAmount = 0;
        List<OrderDetail> refundDetails = getOrderDetailsByRefundId(orderDetail.getOrderNo(), orderDetail.getRefundId());
        for (OrderDetail detail : refundDetails) {
            localAmount += ObjectUtil.isEmpty(detail.getRefundAmount()) ? detail.getOrderAmount() : detail.getRefundAmount();
        }
        return localAmount;
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
    @Transactional(rollbackFor = Exception.class)
    public void abandonPayingOrder(Order order) {
        // 该方法只负责本地落放弃状态，调用前必须已经完成银联关单或确认银联已关闭。
        if (ObjectUtil.isEmpty(order)) {
            return;
        }
        if (!Order.OrderStatusEnum.PAYING.getValue().equals(order.getOrderStatus())) {
            return;
        }
        Integer beforeOrderStatus = order.getOrderStatus();
        // 主订单先改为放弃支付，再同步处理仍处于初始状态的子订单。
        order.setOrderStatus(Order.OrderStatusEnum.ABANDON.getValue());
        super.updateById(order);

        List<OrderDetail> orderDetailList = getOrderDetails(order.getOrderNo());
        List<Long> affectedDetailIds = new ArrayList<>();
        for (OrderDetail orderDetail : orderDetailList) {
            if (!OrderDetail.OrderDetailStatusEnum.INIT.getValue().equals(orderDetail.getOrderStatus())) {
                continue;
            }
            orderDetail.setOrderStatus(OrderDetail.OrderDetailStatusEnum.ABANDON.getValue());
            affectedDetailIds.add(orderDetail.getId());
        }
        if (!orderDetailList.isEmpty()) {
            orderDetailService.updateBatchById(orderDetailList);
        }
        saveOrderAbandonLog(order, beforeOrderStatus, affectedDetailIds);
    }

    /**
     * 用户主动放弃待支付订单。
     *
     * <p>放弃前先查询银联，确认未支付成功后才修改本地状态；如果银联已支付成功，
     * 则复用支付回调逻辑补齐本地支付成功状态；如果银联未支付，则先关单，
     * 关单成功后再修改本地状态。</p>
     *
     * @param orderNo 系统订单号
     * @return 空 Map 表示放弃成功，带 msg 表示失败原因
     * @throws Exception 银联查询接口异常
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map abandonPayingOrder(String orderNo) throws Exception {
        // 第一步：按系统订单号查询订单，只有待支付订单才能主动放弃。
        Order order = getOrderByOrderNo(orderNo);
        if (ObjectUtil.isEmpty(order)) {
            return msgResult("订单不存在");
        }
        if (!Order.OrderStatusEnum.PAYING.getValue().equals(order.getOrderStatus())) {
            return msgResult("订单当前状态不允许放弃支付");
        }

        // 第二步：放弃前先查银联，防止用户实际已支付但本地还没收到回调。
        Museum museum = getRequiredUnionPayMuseum(order);
        JSONObject queryResult = unionPayService.appletQuery(orderNo, museum.getMid(), museum.getTid());
        saveUnionPayQueryLog(order, OrderLog.ACTION_ORDER_ABANDON, OrderLog.SOURCE_USER,
                buildUnionPayQueryRequest(orderNo, museum), queryResult, "用户主动放弃支付前查询银联订单状态");
        if (ObjectUtil.isEmpty(queryResult) || !isUnionSuccessResponse(queryResult)) {
            return msgResult("银联订单状态暂不明确，请稍后再试");
        }
        if (isUnionPayTradeSuccess(queryResult)) {
            syncPaySuccessFromUnionPayQuery(orderNo, queryResult);
            return msgResult("银联已确认订单支付成功，无法放弃支付");
        }
        if (!isUnionPayCanAbandon(queryResult)) {
            return msgResult("银联订单状态暂不允许放弃支付，请稍后再试");
        }
        // 第三步：银联未支付时必须先关单，关单失败不修改本地状态。
        if (!closeUnionPayOrderBeforeAbandon(order, museum, queryResult, OrderLog.SOURCE_USER)) {
            return msgResult("银联关单失败，请稍后再试");
        }

        // 第四步：银联关单成功后，才能把本地主子订单改为放弃支付。
        abandonPayingOrder(order);
        return new HashMap(1);
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
            saveVerificationFailLog(null, ObjectUtil.isEmpty(vo) ? null : vo.getOrderNo(), checkMsg);
            return msgResult(checkMsg);
        }

        // 第二步：校验博物馆是否存在且可用，防止无效博物馆 ID 继续核销。
        checkMsg = checkVerificationMuseum(vo);
        if (ObjectUtil.isNotEmpty(checkMsg)) {
            saveVerificationFailLog(null, vo.getOrderNo(), checkMsg);
            return msgResult(checkMsg);
        }

        // 第三步：查询订单。这里只查未删除订单，避免已删除订单被继续核销。
        Order order = getOrderByOrderNo(vo.getOrderNo());

        // 第四步：校验订单归属、订单状态、预约日期和是否已使用。
        checkMsg = checkVerificationOrder(order, vo);
        if (ObjectUtil.isNotEmpty(checkMsg)) {
            saveVerificationFailLog(order, vo.getOrderNo(), checkMsg);
            return msgResult(checkMsg);
        }

        // 第五步：校验子订单状态，避免主订单数据异常时误核销退款中或未支付的子订单。
        checkMsg = checkVerificationDetails(order);
        if (ObjectUtil.isNotEmpty(checkMsg)) {
            saveVerificationFailLog(order, vo.getOrderNo(), checkMsg);
            return msgResult(checkMsg);
        }

        // 第六步：核销订单。核销成功时同时记录核销时间，方便后台追溯实际使用节点。
        Integer beforeIsUsed = order.getIsUsed();
        markOrderUsed(order);
        saveVerificationSuccessLog(order, beforeIsUsed);
        return new HashMap(1);
    }

    /**
     * 根据游客微信 openId 或团队 ID 分页查询订单列表。
     *
     * <p>游客订单通过 openId 先定位游客，再按 visitorId 查询；团队订单直接按 teamId 查询。
     * 传入 batchNo 时会继续限定游客批次，便于同一团队按不同批次查看订单。
     * 如果两个条件都传入，则查询满足任一条件的订单。分页查询只处理当前页订单，并给每个 Order 的 detailList 和关联表信息字段赋值。</p>
     *
     * @param vo 订单分页查询参数
     * @return 分页订单列表，每条订单包含子订单集合及相关表信息
     */
    @Override
    public PageResult findPage(OrderVO vo) {
        // 第一步：openId 不能直接查订单，先转换为游客 ID；teamId 则可以直接使用。
        Long visitorId = findVisitorIdByWechatOpenid(vo.getOpenId());
        if (ObjectUtil.isEmpty(visitorId) && ObjectUtil.isEmpty(vo.getTeamId())) {
            return emptyOrderPage(vo.getPageNum(), vo.getPageSize());
        }

        // 第二步：只分页查询当前页主订单，避免一次性把用户全部订单拉出来。
        Page<Order> page = findOrderPageByVisitorOrTeam(visitorId, vo.getTeamId(), vo.getBatchNo(), vo.getPageNum(), vo.getPageSize());
        if (page.getRecords().isEmpty()) {
            return PageResultUtil.getPageResult(page);
        }

        // 第三步：当前页主订单查出后，再批量补充子订单列表。
        fillOrderDetailList(page.getRecords());
        // 第四步：批量补充订单和子订单里保存的外键对应的表信息。
        fillOrderRelationInfo(page.getRecords());
        return PageResultUtil.getPageResult(page);
    }

    /**
     * 管理端分页查询所有订单。
     *
     * <p>管理端不限制游客 openId 或团队 ID，默认查询所有未删除订单；
     * 如果前端传入筛选条件，则按订单号、博物馆、订单状态等条件进一步过滤。
     * 查出当前页主订单后，复用游客/团队列表的子订单和关联表补充逻辑。</p>
     *
     * @param vo 订单分页查询参数
     * @return 分页订单列表，每条订单包含子订单集合及相关表信息
     */
    @Override
    public PageResult findAdminPage(OrderVO vo) {
        // 第一步：管理端直接分页查主订单，不强制要求 openId 或 teamId。
        Page<Order> page = findAdminOrderPage(vo);
        if (page.getRecords().isEmpty()) {
            return PageResultUtil.getPageResult(page);
        }

        // 第二步：只给当前页订单批量补充子订单，避免后台全量查询拖垮数据库。
        fillOrderDetailList(page.getRecords());
        // 第三步：批量补充博物馆、游客、团队、活动、场次等关联表信息。
        fillOrderRelationInfo(page.getRecords());
        return PageResultUtil.getPageResult(page);
    }

    /**
     * 根据订单编号查询订单详情。
     *
     * <p>详情接口与列表接口保持同一返回结构：主订单对象中直接带 detailList，
     * 同时补充主订单和子订单涉及的博物馆、游客、团队、活动、场次对象。</p>
     *
     * @param orderNo 订单编号
     * @return 订单详情；订单不存在或已删除时返回 null
     */
    @Override
    public Order findByOrderNo(String orderNo) {
        // 第一步：订单编号是前后端业务流转字段，按订单编号查询未删除主订单。
        Order order = getOrderByOrderNo(orderNo);
        if (ObjectUtil.isEmpty(order)) {
            return null;
        }

        // 第二步：复用列表页填充逻辑，保证详情页的字段结构和列表页一致。
        List<Order> orderList = Collections.singletonList(order);
        fillOrderDetailList(orderList);
        fillOrderRelationInfo(orderList);
        return order;
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
     * @param batchNo   游客批次号
     * @param pageNum   当前页
     * @param pageSize  每页数量
     * @return 分页订单
     */
    private Page<Order> findOrderPageByVisitorOrTeam(Long visitorId, Long teamId, String batchNo, Integer pageNum, Integer pageSize) {
        Page<Order> page = new Page<>(pageNum, pageSize);
        QueryWrapper<Order> orderWrapper = new QueryWrapper<>();
        // 订单列表默认只展示未删除订单，is_deleted 为空的历史数据也按未删除处理。
        orderWrapper.and(wrapper -> wrapper.eq(Order.IS_DELETED, SysConstants.IS_FALSE).or().isNull(Order.IS_DELETED));
        orderWrapper.and(wrapper -> {
            if (ObjectUtil.isNotEmpty(visitorId) && ObjectUtil.isNotEmpty(teamId)) {
                // openId 和 teamId 同时传入时，返回个人订单与团队订单的并集。
                wrapper.eq(Order.VISITOR_ID, visitorId).or().eq(Order.TEAM_ID, teamId);
            } else if (ObjectUtil.isNotEmpty(visitorId)) {
                wrapper.eq(Order.VISITOR_ID, visitorId);
            } else {
                wrapper.eq(Order.TEAM_ID, teamId);
            }
        });
        if (StrUtil.isNotBlank(batchNo)) {
            // 批次号用于区分同一团队的不同批游客，传入时只查询当前批次订单。
            orderWrapper.eq(Order.BATCH_NO, batchNo.trim());
        }
        orderWrapper.orderByDesc(Order.ID);
        return super.page(page, orderWrapper);
    }

    /**
     * 管理端分页查询主订单。
     *
     * @param vo 订单分页查询参数
     * @return 当前页主订单
     */
    private Page<Order> findAdminOrderPage(OrderVO vo) {
        Page<Order> page = new Page<>(vo.getPageNum(), vo.getPageSize());
        QueryWrapper<Order> orderWrapper = buildAdminOrderPageWrapper(vo);
        return super.page(page, orderWrapper);
    }

    /**
     * 构建管理端订单查询条件。
     *
     * <p>这里仅查询主订单表；子订单和活动等关联信息在分页结果出来后再按当前页批量补充。</p>
     *
     * @param vo 订单分页查询参数
     * @return 管理端订单查询条件
     */
    private QueryWrapper<Order> buildAdminOrderPageWrapper(OrderVO vo) {
        QueryWrapper<Order> orderWrapper = new QueryWrapper<>();
        // 后台列表默认只展示未删除订单，兼容 is_deleted 为空的历史数据。
        orderWrapper.and(wrapper -> wrapper.eq(Order.IS_DELETED, SysConstants.IS_FALSE).or().isNull(Order.IS_DELETED));
        if (StrUtil.isNotBlank(vo.getOrderNo())) {
            orderWrapper.like(Order.ORDER_NO, vo.getOrderNo().trim());
        }
        if (ObjectUtil.isNotEmpty(vo.getMuseumId())) {
            orderWrapper.eq(Order.MUSEUM_ID, vo.getMuseumId());
        }
        if (ObjectUtil.isNotEmpty(vo.getVisitorId())) {
            orderWrapper.eq(Order.VISITOR_ID, vo.getVisitorId());
        }
        if (ObjectUtil.isNotEmpty(vo.getTeamId())) {
            orderWrapper.eq(Order.TEAM_ID, vo.getTeamId());
        }
        if (StrUtil.isNotBlank(vo.getBatchNo())) {
            orderWrapper.eq(Order.BATCH_NO, vo.getBatchNo().trim());
        }
        if (ObjectUtil.isNotEmpty(vo.getOrderType())) {
            orderWrapper.eq(Order.ORDER_TYPE, vo.getOrderType());
        }
        if (ObjectUtil.isNotEmpty(vo.getOrderStatus())) {
            orderWrapper.eq(Order.ORDER_STATUS, vo.getOrderStatus());
        }
        if (ObjectUtil.isNotEmpty(vo.getIsUsed())) {
            orderWrapper.eq(Order.IS_USED, vo.getIsUsed());
        }
        if (ObjectUtil.isNotEmpty(vo.getAppointmentDate())) {
            orderWrapper.eq(Order.APPOINTMENT_DATE, vo.getAppointmentDate());
        }
        orderWrapper.orderByDesc(Order.ID);
        return orderWrapper;
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

        // 按当前页订单号一次性查出子订单，避免每个订单循环查库。
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
     * 给当前页订单补充关联表信息。
     *
     * <p>主订单补充博物馆、游客、团队；子订单补充博物馆、活动、活动场次。
     * 所有关联表都按当前页涉及的 ID 批量查询，避免 N+1 查询问题。</p>
     *
     * @param orderList 当前页主订单列表
     */
    private void fillOrderRelationInfo(List<Order> orderList) {
        if (ObjectUtil.isEmpty(orderList)) {
            return;
        }
        Set<Long> museumIds = new HashSet<>();
        Set<Long> visitorIds = new HashSet<>();
        Set<Long> teamIds = new HashSet<>();
        Set<Long> activityIds = new HashSet<>();
        Set<Long> activityScheduleIds = new HashSet<>();
        List<OrderDetail> allDetailList = new ArrayList<>();

        for (Order order : orderList) {
            // 先收集主订单上的外键 ID，后面统一批量查询对应表。
            addId(museumIds, order.getMuseumId());
            addId(visitorIds, order.getVisitorId());
            addId(teamIds, order.getTeamId());
            if (ObjectUtil.isEmpty(order.getDetailList())) {
                continue;
            }
            for (OrderDetail orderDetail : order.getDetailList()) {
                allDetailList.add(orderDetail);
                // 子订单上也保存了博物馆、活动和场次 ID，需要一起补充对应表信息。
                addId(museumIds, orderDetail.getMuseumId());
                addId(activityIds, orderDetail.getActivityId());
                addId(activityScheduleIds, orderDetail.getActivityScheduleId());
            }
        }

        Map<Long, Museum> museumMap = getMuseumMap(museumIds);
        Map<Long, Visitor> visitorMap = getVisitorMap(visitorIds);
        Map<Long, Team> teamMap = getTeamMap(teamIds);
        List<Visitor> teamVisitorList = getTeamVisitors(teamIds);
        Map<Long, List<Visitor>> teamVisitorMap = groupTeamVisitorsByTeam(teamVisitorList);
        Map<String, List<Visitor>> teamBatchVisitorMap = groupTeamVisitorsByTeamAndBatch(teamVisitorList);
        Map<Long, ActivityManage> activityManageMap = getActivityManageMap(activityIds);
        Map<Long, ActivitySchedule> activityScheduleMap = getActivityScheduleMap(activityScheduleIds);

        for (Order order : orderList) {
            // 主订单直接挂上外键对应的对象，前端不用再根据 ID 反查。
            order.setMuseum(museumMap.get(order.getMuseumId()));
            order.setVisitor(visitorMap.get(order.getVisitorId()));
            // 团队订单返回团队信息时，同步带上该订单批次的游客列表。
            order.setTeam(buildOrderTeam(order, teamMap, teamVisitorMap, teamBatchVisitorMap));
        }
        for (OrderDetail orderDetail : allDetailList) {
            // 子订单补充活动和场次详情，列表页可以直接展示活动名称、场次时间等信息。
            orderDetail.setMuseum(museumMap.get(orderDetail.getMuseumId()));
            orderDetail.setActivityManage(activityManageMap.get(orderDetail.getActivityId()));
            orderDetail.setActivitySchedule(activityScheduleMap.get(orderDetail.getActivityScheduleId()));
        }
    }

    /**
     * 收集非空 ID，避免批量查询时出现空值。
     *
     * @param idSet ID 集合
     * @param id    待加入的 ID
     */
    private void addId(Set<Long> idSet, Long id) {
        if (ObjectUtil.isNotEmpty(id)) {
            idSet.add(id);
        }
    }

    /**
     * 批量查询博物馆并转换为 id -> Museum 的 Map。
     */
    private Map<Long, Museum> getMuseumMap(Set<Long> museumIds) {
        return ObjectUtil.isEmpty(museumIds) ? Collections.emptyMap() : toIdMap(museumService.listByIds(museumIds), Museum::getId);
    }

    /**
     * 批量查询游客并转换为 id -> Visitor 的 Map。
     */
    private Map<Long, Visitor> getVisitorMap(Set<Long> visitorIds) {
        return ObjectUtil.isEmpty(visitorIds) ? Collections.emptyMap() : toIdMap(visitorService.listByIds(visitorIds), Visitor::getId);
    }

    /**
     * 批量查询团队并转换为 id -> Team 的 Map。
     */
    private Map<Long, Team> getTeamMap(Set<Long> teamIds) {
        return ObjectUtil.isEmpty(teamIds) ? Collections.emptyMap() : toIdMap(teamService.listByIds(teamIds), Team::getId);
    }

    /**
     * 批量查询当前页团队订单涉及的游客。
     */
    private List<Visitor> getTeamVisitors(Set<Long> teamIds) {
        if (ObjectUtil.isEmpty(teamIds)) {
            return Collections.emptyList();
        }
        QueryWrapper<Visitor> visitorWrapper = new QueryWrapper<>();
        visitorWrapper.in(Visitor.TEAM_ID, teamIds);
        visitorWrapper.and(wrapper -> wrapper.eq(Visitor.IS_DELETED, SysConstants.IS_FALSE).or().isNull(Visitor.IS_DELETED));
        return visitorService.list(visitorWrapper);
    }

    /**
     * 按团队 ID 分组团队游客，用于兼容历史订单没有 batchNo 的情况。
     */
    private Map<Long, List<Visitor>> groupTeamVisitorsByTeam(List<Visitor> visitorList) {
        Map<Long, List<Visitor>> visitorMap = new HashMap<>();
        if (ObjectUtil.isEmpty(visitorList)) {
            return visitorMap;
        }
        for (Visitor visitor : visitorList) {
            if (ObjectUtil.isEmpty(visitor.getTeamId())) {
                continue;
            }
            visitorMap.computeIfAbsent(visitor.getTeamId(), key -> new ArrayList<>()).add(visitor);
        }
        return visitorMap;
    }

    /**
     * 按团队 ID + 批次号分组团队游客，确保同一团队不同批次订单返回各自批次的游客。
     */
    private Map<String, List<Visitor>> groupTeamVisitorsByTeamAndBatch(List<Visitor> visitorList) {
        Map<String, List<Visitor>> visitorMap = new HashMap<>();
        if (ObjectUtil.isEmpty(visitorList)) {
            return visitorMap;
        }
        for (Visitor visitor : visitorList) {
            if (ObjectUtil.isEmpty(visitor.getTeamId()) || StrUtil.isBlank(visitor.getBatchNo())) {
                continue;
            }
            visitorMap.computeIfAbsent(buildTeamBatchKey(visitor.getTeamId(), visitor.getBatchNo()), key -> new ArrayList<>()).add(visitor);
        }
        return visitorMap;
    }

    /**
     * 构建订单返回用的团队对象。
     *
     * <p>同一团队可能在当前页出现多个批次订单，所以不能复用 teamMap 里的同一个 Team 实例；
     * 每笔订单复制一个团队对象后，再写入该订单对应批次的游客列表。</p>
     */
    private Team buildOrderTeam(Order order, Map<Long, Team> teamMap,
                                Map<Long, List<Visitor>> teamVisitorMap,
                                Map<String, List<Visitor>> teamBatchVisitorMap) {
        if (ObjectUtil.isEmpty(order.getTeamId())) {
            return null;
        }
        Team team = teamMap.get(order.getTeamId());
        if (ObjectUtil.isEmpty(team)) {
            return null;
        }
        Team orderTeam = BeanUtil.copyProperties(team, Team.class);
        List<Visitor> visitorList = StrUtil.isBlank(order.getBatchNo())
                ? teamVisitorMap.getOrDefault(order.getTeamId(), Collections.emptyList())
                : teamBatchVisitorMap.getOrDefault(buildTeamBatchKey(order.getTeamId(), order.getBatchNo()), Collections.emptyList());
        orderTeam.setVisitorList(visitorList);
        return orderTeam;
    }

    /**
     * 构建团队批次分组 key。
     */
    private String buildTeamBatchKey(Long teamId, String batchNo) {
        return teamId + ":" + batchNo.trim();
    }

    /**
     * 批量查询活动并转换为 id -> ActivityManage 的 Map。
     */
    private Map<Long, ActivityManage> getActivityManageMap(Set<Long> activityIds) {
        return ObjectUtil.isEmpty(activityIds) ? Collections.emptyMap() : toIdMap(activityManageService.listByIds(activityIds), ActivityManage::getId);
    }

    /**
     * 批量查询活动场次并转换为 id -> ActivitySchedule 的 Map。
     */
    private Map<Long, ActivitySchedule> getActivityScheduleMap(Set<Long> activityScheduleIds) {
        return ObjectUtil.isEmpty(activityScheduleIds) ? Collections.emptyMap() : toIdMap(activityScheduleService.listByIds(activityScheduleIds), ActivitySchedule::getId);
    }

    /**
     * 把批量查询结果转换成以主键 ID 为 key 的 Map。
     *
     * @param records  数据库查询结果
     * @param idGetter 获取主键 ID 的方法
     * @param <T>      实体类型
     * @return id -> 实体对象
     */
    private <T> Map<Long, T> toIdMap(Collection<T> records, Function<T, Long> idGetter) {
        if (ObjectUtil.isEmpty(records)) {
            return Collections.emptyMap();
        }
        Map<Long, T> result = new HashMap<>();
        for (T record : records) {
            Long id = idGetter.apply(record);
            if (ObjectUtil.isNotEmpty(id)) {
                result.put(id, record);
            }
        }
        return result;
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
        try {
            Long.valueOf(vo.getMuseumId());
        } catch (NumberFormatException e) {
            return "博物馆ID格式有误";
        }
        return null;
    }

    /**
     * 校验核销入口传入的博物馆是否可用。
     *
     * @param vo 核销参数
     * @return null 表示校验通过，否则返回前端提示
     */
    private String checkVerificationMuseum(VerificationVO vo) {
        Museum museum = museumService.getById(Long.valueOf(vo.getMuseumId()));
        if (ObjectUtil.isEmpty(museum) || !SysConstants.IS_TRUE.equals(museum.getStatus())) {
            return "博物馆不存在或已禁用";
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
        Long museumId = Long.valueOf(vo.getMuseumId());
        if (ObjectUtil.isEmpty(order.getMuseumId()) || !order.getMuseumId().equals(museumId)) {
            return "订单不属于当前博物馆，无法核销";
        }
        if (SysConstants.IS_TRUE.equals(order.getIsUsed())) {
            return "订单已核销，请勿重复操作";
        }
        if (ObjectUtil.isEmpty(order.getAppointmentDate())) {
            return "订单预约日期异常，无法核销";
        }
        if (order.getAppointmentDate().isAfter(LocalDate.now())) {
            return "预约日期未到，无法核销";
        }
        if (order.getAppointmentDate().isBefore(LocalDate.now())) {
            return "预约日期已过期，无法核销";
        }
        if (Order.OrderStatusEnum.PAYING.getValue().equals(order.getOrderStatus())) {
            return "订单未支付，无法核销";
        }
        if (Order.OrderStatusEnum.ABANDON.getValue().equals(order.getOrderStatus())) {
            return "订单已放弃支付，无法核销";
        }
        if (Order.OrderStatusEnum.REFUNDING.getValue().equals(order.getOrderStatus())) {
            return "订单退款中，无法核销";
        }
        if (Order.OrderStatusEnum.PARTIAL_REFUND.getValue().equals(order.getOrderStatus())
                || Order.OrderStatusEnum.ALL_REFUND.getValue().equals(order.getOrderStatus())) {
            return "订单已发生退款，无法核销";
        }
        if (!Order.OrderStatusEnum.SUCCESS.getValue().equals(order.getOrderStatus())) {
            return "订单状态不允许核销";
        }
        return null;
    }

    /**
     * 校验订单子订单是否都允许核销。
     *
     * <p>核销虽然只更新主订单 isUsed，但子订单才代表实际购买的活动名额；
     * 如果子订单缺失、数量异常或存在非支付成功状态，说明订单数据不完整，不能核销。</p>
     *
     * @param order 主订单
     * @return null 表示校验通过，否则返回前端提示
     */
    private String checkVerificationDetails(Order order) {
        List<OrderDetail> orderDetailList = getOrderDetails(order.getOrderNo());
        if (ObjectUtil.isEmpty(orderDetailList)) {
            return "订单子订单不存在，无法核销";
        }
        if (ObjectUtil.isNotEmpty(order.getOrderQuantity()) && orderDetailList.size() != order.getOrderQuantity()) {
            return "订单子订单数量异常，无法核销";
        }
        for (OrderDetail orderDetail : orderDetailList) {
            if (OrderDetail.OrderDetailStatusEnum.REFUNDING.getValue().equals(orderDetail.getOrderStatus())
                    || OrderDetail.OrderDetailStatusEnum.REFUND.getValue().equals(orderDetail.getOrderStatus())) {
                return "存在退款中或已退款的子订单，无法核销";
            }
            if (!OrderDetail.OrderDetailStatusEnum.PAY_SUCCESS.getValue().equals(orderDetail.getOrderStatus())) {
                return "存在未支付成功的子订单，无法核销";
            }
        }
        return null;
    }

    /**
     * 将订单标记为已核销。
     *
     * @param order 主订单
     */
    private void markOrderUsed(Order order) {
        // isUsed 表示最终核销结果，verificationTime 记录本次核销实际发生时间。
        order.setIsUsed(SysConstants.IS_TRUE);
        order.setVerificationTime(LocalDateTime.now());
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
     * 退款失败回退后刷新主订单状态。
     *
     * <p>若主订单下已经没有退款中或已退款子订单，则恢复为支付成功并清空退款汇总；
     * 否则继续按子订单状态汇总主订单退款信息。</p>
     *
     * @param order 主订单
     */
    private void refreshOrderAfterRefundFailed(Order order) {
        List<OrderDetail> orderDetailList = getOrderDetails(order.getOrderNo());
        boolean hasRefundOrRefunding = false;
        for (OrderDetail orderDetail : orderDetailList) {
            if (OrderDetail.OrderDetailStatusEnum.REFUND.getValue().equals(orderDetail.getOrderStatus())
                    || OrderDetail.OrderDetailStatusEnum.REFUNDING.getValue().equals(orderDetail.getOrderStatus())) {
                hasRefundOrRefunding = true;
                break;
            }
        }
        if (hasRefundOrRefunding) {
            refreshOrderRefundInfo(order);
            return;
        }
        order.setOrderStatus(Order.OrderStatusEnum.SUCCESS.getValue());
        order.setRefundAmount(0);
        order.setRefundQuantity(0);
        super.updateById(order);
    }

    /**
     * 记录订单创建日志。
     *
     * @param order 已保存的待支付订单
     * @param vo    下单请求参数
     */
    private void saveOrderCreatedLog(Order order, AppointmentVO vo) {
        OrderLog orderLog = buildOrderLog(order, OrderLog.LOG_TYPE_ORDER_STATUS,
                OrderLog.ACTION_ORDER_CREATE, OrderLog.SOURCE_USER);
        orderLog.setAfterOrderStatus(order.getOrderStatus());
        orderLog.setAfterIsUsed(order.getIsUsed());
        orderLog.setTradeAmount(order.getPayAmount());
        orderLog.setRequestContent(JSON.toJSONString(vo));
        orderLog.setResponseContent("已创建待支付订单并获取小程序支付参数");
        orderLog.setSuccess(SysConstants.IS_TRUE);
        orderLog.setRemark("创建待支付订单");
        saveOrderLog(orderLog);
    }

    /**
     * 记录银联支付查询日志。
     *
     * @param order          本地订单
     * @param bizAction      业务动作
     * @param eventSource    事件来源
     * @param requestContent 查询请求摘要
     * @param response       银联查询响应
     * @param remark         备注
     */
    private void saveUnionPayQueryLog(Order order, String bizAction, String eventSource,
                                      String requestContent, JSONObject response, String remark) {
        OrderLog orderLog = buildOrderLog(order, OrderLog.LOG_TYPE_PAY, bizAction, eventSource);
        orderLog.setBeforeOrderStatus(order.getOrderStatus());
        orderLog.setAfterOrderStatus(order.getOrderStatus());
        orderLog.setTradeAmount(order.getPayAmount());
        orderLog.setRequestContent(requestContent);
        fillUnionPayResponse(orderLog, response);
        orderLog.setSuccess(isUnionSuccessResponse(response) ? SysConstants.IS_TRUE : SysConstants.IS_FALSE);
        orderLog.setRemark(ObjectUtil.isEmpty(remark) ? "查询银联支付订单状态" : remark);
        saveOrderLog(orderLog);
    }

    /**
     * 记录银联关单日志。
     *
     * @param order          本地订单
     * @param eventSource    事件来源
     * @param requestContent 关单请求摘要
     * @param response       银联关单响应
     * @param remark         备注
     */
    private void saveUnionPayCloseLog(Order order, String eventSource, String requestContent, JSONObject response, String remark) {
        OrderLog orderLog = buildOrderLog(order, OrderLog.LOG_TYPE_PAY, OrderLog.ACTION_PAY_CLOSE, eventSource);
        orderLog.setBeforeOrderStatus(order.getOrderStatus());
        orderLog.setAfterOrderStatus(order.getOrderStatus());
        orderLog.setTradeAmount(order.getPayAmount());
        orderLog.setRequestContent(requestContent);
        fillUnionPayResponse(orderLog, response);
        orderLog.setSuccess(isUnionSuccessResponse(response) || isUnionPayTradeClosed(response)
                ? SysConstants.IS_TRUE : SysConstants.IS_FALSE);
        orderLog.setRemark(ObjectUtil.isEmpty(remark) ? "放弃订单前关闭银联未支付订单" : remark);
        saveOrderLog(orderLog);
    }

    /**
     * 记录银联关单异常日志。
     *
     * @param order          本地订单
     * @param eventSource    事件来源
     * @param requestContent 关单请求摘要
     * @param e              异常信息
     */
    private void saveUnionPayCloseExceptionLog(Order order, String eventSource, String requestContent, Exception e) {
        OrderLog orderLog = buildOrderLog(order, OrderLog.LOG_TYPE_PAY, OrderLog.ACTION_PAY_CLOSE, eventSource);
        orderLog.setBeforeOrderStatus(order.getOrderStatus());
        orderLog.setAfterOrderStatus(order.getOrderStatus());
        orderLog.setTradeAmount(order.getPayAmount());
        orderLog.setRequestContent(requestContent);
        orderLog.setSuccess(SysConstants.IS_FALSE);
        orderLog.setExceptionMessage(limitText(e.getMessage(), 1000));
        orderLog.setRemark("银联关单异常，本地订单未放弃");
        saveOrderLog(orderLog);
    }

    /**
     * 构建银联支付查询请求摘要。
     *
     * @param orderNo 系统订单号
     * @param museum  订单所属博物馆
     * @return 查询请求 JSON
     */
    private String buildUnionPayQueryRequest(String orderNo, Museum museum) {
        JSONObject request = new JSONObject();
        request.put("merOrderId", orderNo);
        request.put("mid", museum.getMid());
        request.put("tid", museum.getTid());
        return request.toString();
    }

    /**
     * 构建银联关单请求摘要。
     *
     * @param orderNo 系统订单号
     * @param museum  订单所属博物馆
     * @return 关单请求 JSON
     */
    private String buildUnionPayCloseRequest(String orderNo, Museum museum) {
        JSONObject request = new JSONObject();
        request.put("merOrderId", orderNo);
        request.put("instMid", "MINIDEFAULT");
        request.put("mid", museum.getMid());
        request.put("tid", museum.getTid());
        return request.toString();
    }

    /**
     * 记录支付回调成功更新订单日志。
     */
    private void savePayNotifySuccessLog(Order order, String tradeNo, Integer totalAmount, String requestString,
                                         Integer beforeOrderStatus, List<Long> affectedDetailIds) {
        OrderLog orderLog = buildOrderLog(order, OrderLog.LOG_TYPE_PAY,
                OrderLog.ACTION_PAY_NOTIFY, OrderLog.SOURCE_UNIONPAY_NOTIFY);
        orderLog.setBeforeOrderStatus(beforeOrderStatus);
        orderLog.setAfterOrderStatus(order.getOrderStatus());
        orderLog.setBeforeDetailStatus(getPayNotifyBeforeDetailStatus(beforeOrderStatus));
        orderLog.setAfterDetailStatus(OrderDetail.OrderDetailStatusEnum.PAY_SUCCESS.getValue());
        orderLog.setAffectedDetailIds(joinLongIds(affectedDetailIds));
        orderLog.setUnionpayOrderNo(tradeNo);
        orderLog.setUnionpayStatus(SysConstants.TRADE_SUCCESS);
        orderLog.setTradeAmount(totalAmount);
        orderLog.setRequestContent(requestString);
        orderLog.setResponseContent("主订单和初始子订单已更新为支付成功");
        orderLog.setSuccess(SysConstants.IS_TRUE);
        orderLog.setRemark("银联确认支付成功，本地订单状态已更新");
        saveOrderLog(orderLog);
    }

    /**
     * 获取支付回调日志中的子订单变更前状态。
     *
     * @param beforeOrderStatus 主订单变更前状态
     * @return 子订单变更前状态
     */
    private Integer getPayNotifyBeforeDetailStatus(Integer beforeOrderStatus) {
        if (Order.OrderStatusEnum.ABANDON.getValue().equals(beforeOrderStatus)) {
            return OrderDetail.OrderDetailStatusEnum.ABANDON.getValue();
        }
        return OrderDetail.OrderDetailStatusEnum.INIT.getValue();
    }

    /**
     * 记录支付回调幂等跳过日志。
     */
    private void savePayNotifySkipLog(Order order, String tradeNo, Integer totalAmount, String requestString, Integer beforeOrderStatus) {
        OrderLog orderLog = buildOrderLog(order, OrderLog.LOG_TYPE_PAY,
                OrderLog.ACTION_PAY_NOTIFY, OrderLog.SOURCE_UNIONPAY_NOTIFY);
        orderLog.setBeforeOrderStatus(beforeOrderStatus);
        orderLog.setAfterOrderStatus(order.getOrderStatus());
        orderLog.setUnionpayOrderNo(tradeNo);
        orderLog.setUnionpayStatus(SysConstants.TRADE_SUCCESS);
        orderLog.setTradeAmount(totalAmount);
        orderLog.setRequestContent(requestString);
        orderLog.setResponseContent("订单状态已支付或已进入退款链路，本次支付回调未修改订单");
        orderLog.setSuccess(SysConstants.IS_TRUE);
        orderLog.setRemark("支付回调幂等返回");
        saveOrderLog(orderLog);
    }

    /**
     * 记录支付回调异常日志。
     */
    private void savePayNotifyExceptionLog(Order order, String orderNo, String tradeNo, Integer totalAmount,
                                           String requestString, RuntimeException e) {
        OrderLog orderLog = ObjectUtil.isEmpty(order)
                ? buildOrderLog(orderNo, OrderLog.LOG_TYPE_PAY, OrderLog.ACTION_PAY_NOTIFY, OrderLog.SOURCE_UNIONPAY_NOTIFY)
                : buildOrderLog(order, OrderLog.LOG_TYPE_PAY, OrderLog.ACTION_PAY_NOTIFY, OrderLog.SOURCE_UNIONPAY_NOTIFY);
        orderLog.setBeforeOrderStatus(ObjectUtil.isEmpty(order) ? null : order.getOrderStatus());
        orderLog.setAfterOrderStatus(ObjectUtil.isEmpty(order) ? null : order.getOrderStatus());
        orderLog.setUnionpayOrderNo(tradeNo);
        orderLog.setUnionpayStatus(SysConstants.TRADE_SUCCESS);
        orderLog.setTradeAmount(totalAmount);
        orderLog.setRequestContent(requestString);
        orderLog.setExceptionMessage(limitText(e.getMessage(), 1000));
        orderLog.setSuccess(SysConstants.IS_FALSE);
        orderLog.setRemark("支付回调处理异常");
        saveOrderLog(orderLog);
    }

    /**
     * 记录退款申请校验或银联受理失败日志。
     */
    private void saveRefundApplyCheckFailLog(Order order, List<OrderDetail> orderDetails, String refundOrderId,
                                             JSONObject response, String remark, Integer beforeOrderStatus,
                                             Integer beforeDetailStatus, Integer beforeRefundAmount,
                                             Integer beforeRefundQuantity) {
        OrderLog orderLog = buildOrderLog(order, OrderLog.LOG_TYPE_REFUND,
                OrderLog.ACTION_REFUND_APPLY, OrderLog.SOURCE_USER);
        orderLog.setOrderDetailId(firstDetailId(orderDetails));
        orderLog.setAffectedDetailIds(joinDetailIds(orderDetails));
        orderLog.setBeforeOrderStatus(beforeOrderStatus);
        orderLog.setAfterOrderStatus(order.getOrderStatus());
        orderLog.setBeforeDetailStatus(beforeDetailStatus);
        orderLog.setAfterDetailStatus(beforeDetailStatus);
        orderLog.setBeforeRefundAmount(beforeRefundAmount);
        orderLog.setAfterRefundAmount(order.getRefundAmount());
        orderLog.setBeforeRefundQuantity(beforeRefundQuantity);
        orderLog.setAfterRefundQuantity(order.getRefundQuantity());
        orderLog.setRefundOrderId(refundOrderId);
        fillUnionPayResponse(orderLog, response);
        orderLog.setSuccess(SysConstants.IS_FALSE);
        orderLog.setRemark(remark);
        saveOrderLog(orderLog);
    }

    /**
     * 记录退款申请成功日志。
     */
    private void saveRefundApplySuccessLog(Order order, List<OrderDetail> orderDetails, String refundOrderId,
                                           Integer money, JSONObject response, Integer beforeOrderStatus,
                                           Integer beforeDetailStatus, Integer beforeRefundAmount,
                                           Integer beforeRefundQuantity) {
        OrderLog orderLog = buildOrderLog(order, OrderLog.LOG_TYPE_REFUND,
                OrderLog.ACTION_REFUND_APPLY, OrderLog.SOURCE_USER);
        orderLog.setOrderDetailId(firstDetailId(orderDetails));
        orderLog.setAffectedDetailIds(joinDetailIds(orderDetails));
        orderLog.setBeforeOrderStatus(beforeOrderStatus);
        orderLog.setAfterOrderStatus(order.getOrderStatus());
        orderLog.setBeforeDetailStatus(beforeDetailStatus);
        orderLog.setAfterDetailStatus(OrderDetail.OrderDetailStatusEnum.REFUNDING.getValue());
        orderLog.setBeforeRefundAmount(beforeRefundAmount);
        orderLog.setAfterRefundAmount(order.getRefundAmount());
        orderLog.setBeforeRefundQuantity(beforeRefundQuantity);
        orderLog.setAfterRefundQuantity(order.getRefundQuantity());
        orderLog.setRefundOrderId(refundOrderId);
        orderLog.setTradeAmount(money);
        fillUnionPayResponse(orderLog, response);
        orderLog.setSuccess(SysConstants.IS_TRUE);
        orderLog.setRemark("银联已受理退款申请，本地订单已标记为退款中");
        saveOrderLog(orderLog);
    }

    /**
     * 记录退款回调成功更新订单日志。
     */
    private void saveRefundNotifySuccessLog(Order order, List<OrderDetail> orderDetails, String tradeNo,
                                            Integer money, String refundOrderId, String refundTime,
                                            String requestString, Integer beforeOrderStatus,
                                            Integer beforeDetailStatus, Integer beforeRefundAmount,
                                            Integer beforeRefundQuantity) {
        OrderLog orderLog = buildRefundNotifyLog(order, orderDetails, tradeNo, money, refundOrderId,
                refundTime, requestString, beforeOrderStatus, beforeDetailStatus, beforeRefundAmount, beforeRefundQuantity);
        orderLog.setAfterOrderStatus(order.getOrderStatus());
        orderLog.setAfterDetailStatus(OrderDetail.OrderDetailStatusEnum.REFUND.getValue());
        orderLog.setAfterRefundAmount(order.getRefundAmount());
        orderLog.setAfterRefundQuantity(order.getRefundQuantity());
        orderLog.setResponseContent("退款回调已更新子订单退款状态，并刷新主订单退款汇总");
        orderLog.setSuccess(SysConstants.IS_TRUE);
        orderLog.setRemark("银联确认退款成功，本地订单退款状态已更新");
        saveOrderLog(orderLog);
    }

    /**
     * 记录退款回调幂等跳过日志。
     */
    private void saveRefundNotifySkipLog(Order order, List<OrderDetail> orderDetails, String tradeNo,
                                         Integer money, String refundOrderId, String refundTime,
                                         String requestString, String remark, Integer beforeOrderStatus,
                                         Integer beforeDetailStatus, Integer beforeRefundAmount,
                                         Integer beforeRefundQuantity) {
        OrderLog orderLog = buildRefundNotifyLog(order, orderDetails, tradeNo, money, refundOrderId,
                refundTime, requestString, beforeOrderStatus, beforeDetailStatus, beforeRefundAmount, beforeRefundQuantity);
        orderLog.setAfterOrderStatus(order.getOrderStatus());
        orderLog.setAfterDetailStatus(beforeDetailStatus);
        orderLog.setAfterRefundAmount(order.getRefundAmount());
        orderLog.setAfterRefundQuantity(order.getRefundQuantity());
        orderLog.setResponseContent("本次退款回调未修改订单");
        orderLog.setSuccess(SysConstants.IS_TRUE);
        orderLog.setRemark(remark);
        saveOrderLog(orderLog);
    }

    /**
     * 记录退款回调异常日志。
     */
    private void saveRefundNotifyExceptionLog(Order order, String orderNo, String tradeNo, Integer money,
                                              String refundOrderId, String refundTime, String requestString,
                                              RuntimeException e) {
        OrderLog orderLog = ObjectUtil.isEmpty(order)
                ? buildOrderLog(orderNo, OrderLog.LOG_TYPE_REFUND, OrderLog.ACTION_REFUND_NOTIFY, OrderLog.SOURCE_UNIONPAY_NOTIFY)
                : buildOrderLog(order, OrderLog.LOG_TYPE_REFUND, OrderLog.ACTION_REFUND_NOTIFY, OrderLog.SOURCE_UNIONPAY_NOTIFY);
        orderLog.setBeforeOrderStatus(ObjectUtil.isEmpty(order) ? null : order.getOrderStatus());
        orderLog.setAfterOrderStatus(ObjectUtil.isEmpty(order) ? null : order.getOrderStatus());
        orderLog.setUnionpayOrderNo(tradeNo);
        orderLog.setRefundOrderId(refundOrderId);
        orderLog.setUnionpayStatus(SysConstants.REFUND_SUCCESS);
        orderLog.setUnionpayTradeTime(refundTime);
        orderLog.setTradeAmount(money);
        orderLog.setRequestContent(requestString);
        orderLog.setExceptionMessage(limitText(e.getMessage(), 1000));
        orderLog.setSuccess(SysConstants.IS_FALSE);
        orderLog.setRemark("退款回调处理异常");
        saveOrderLog(orderLog);
    }

    /**
     * 构建退款回调日志公共字段。
     */
    private OrderLog buildRefundNotifyLog(Order order, List<OrderDetail> orderDetails, String tradeNo,
                                          Integer money, String refundOrderId, String refundTime,
                                          String requestString, Integer beforeOrderStatus,
                                          Integer beforeDetailStatus, Integer beforeRefundAmount,
                                          Integer beforeRefundQuantity) {
        OrderLog orderLog = buildOrderLog(order, OrderLog.LOG_TYPE_REFUND,
                OrderLog.ACTION_REFUND_NOTIFY, OrderLog.SOURCE_UNIONPAY_NOTIFY);
        orderLog.setOrderDetailId(firstDetailId(orderDetails));
        orderLog.setAffectedDetailIds(joinDetailIds(orderDetails));
        orderLog.setBeforeOrderStatus(beforeOrderStatus);
        orderLog.setBeforeDetailStatus(beforeDetailStatus);
        orderLog.setBeforeRefundAmount(beforeRefundAmount);
        orderLog.setBeforeRefundQuantity(beforeRefundQuantity);
        orderLog.setUnionpayOrderNo(tradeNo);
        orderLog.setRefundOrderId(refundOrderId);
        orderLog.setUnionpayStatus(SysConstants.REFUND_SUCCESS);
        orderLog.setUnionpayTradeTime(refundTime);
        orderLog.setTradeAmount(money);
        orderLog.setRequestContent(requestString);
        return orderLog;
    }

    /**
     * 记录退款失败回退成功日志。
     */
    private void saveRefundRollbackSuccessLog(Order order, List<Long> affectedDetailIds, String refundOrderId,
                                              Integer beforeOrderStatus, Integer beforeDetailStatus,
                                              Integer beforeRefundAmount, Integer beforeRefundQuantity) {
        OrderLog orderLog = buildOrderLog(order, OrderLog.LOG_TYPE_REFUND,
                OrderLog.ACTION_REFUND_FAIL_ROLLBACK, OrderLog.SOURCE_UNIONPAY_QUERY);
        orderLog.setAffectedDetailIds(joinLongIds(affectedDetailIds));
        orderLog.setBeforeOrderStatus(beforeOrderStatus);
        orderLog.setAfterOrderStatus(order.getOrderStatus());
        orderLog.setBeforeDetailStatus(beforeDetailStatus);
        orderLog.setAfterDetailStatus(OrderDetail.OrderDetailStatusEnum.PAY_SUCCESS.getValue());
        orderLog.setBeforeRefundAmount(beforeRefundAmount);
        orderLog.setAfterRefundAmount(order.getRefundAmount());
        orderLog.setBeforeRefundQuantity(beforeRefundQuantity);
        orderLog.setAfterRefundQuantity(order.getRefundQuantity());
        orderLog.setRefundOrderId(refundOrderId);
        orderLog.setUnionpayStatus(SysConstants.REFUND_FAIL);
        orderLog.setResponseContent("银联退款失败，本地退款中状态已回退");
        orderLog.setSuccess(SysConstants.IS_TRUE);
        orderLog.setRemark("退款失败回退本地订单状态");
        saveOrderLog(orderLog);
    }

    /**
     * 记录退款失败回退跳过日志。
     */
    private void saveRefundRollbackSkipLog(Order order, List<OrderDetail> orderDetails, String refundOrderId,
                                           String remark, Integer beforeOrderStatus, Integer beforeDetailStatus,
                                           Integer beforeRefundAmount, Integer beforeRefundQuantity) {
        OrderLog orderLog = buildOrderLog(order, OrderLog.LOG_TYPE_REFUND,
                OrderLog.ACTION_REFUND_FAIL_ROLLBACK, OrderLog.SOURCE_UNIONPAY_QUERY);
        orderLog.setOrderDetailId(firstDetailId(orderDetails));
        orderLog.setAffectedDetailIds(joinDetailIds(orderDetails));
        orderLog.setBeforeOrderStatus(beforeOrderStatus);
        orderLog.setAfterOrderStatus(order.getOrderStatus());
        orderLog.setBeforeDetailStatus(beforeDetailStatus);
        orderLog.setAfterDetailStatus(beforeDetailStatus);
        orderLog.setBeforeRefundAmount(beforeRefundAmount);
        orderLog.setAfterRefundAmount(order.getRefundAmount());
        orderLog.setBeforeRefundQuantity(beforeRefundQuantity);
        orderLog.setAfterRefundQuantity(order.getRefundQuantity());
        orderLog.setRefundOrderId(refundOrderId);
        orderLog.setUnionpayStatus(SysConstants.REFUND_FAIL);
        orderLog.setSuccess(SysConstants.IS_TRUE);
        orderLog.setRemark(remark);
        saveOrderLog(orderLog);
    }

    /**
     * 记录退款失败回退异常日志。
     */
    private void saveRefundRollbackExceptionLog(Order order, String orderNo, String refundOrderId, String remark) {
        OrderLog orderLog = ObjectUtil.isEmpty(order)
                ? buildOrderLog(orderNo, OrderLog.LOG_TYPE_REFUND, OrderLog.ACTION_REFUND_FAIL_ROLLBACK, OrderLog.SOURCE_UNIONPAY_QUERY)
                : buildOrderLog(order, OrderLog.LOG_TYPE_REFUND, OrderLog.ACTION_REFUND_FAIL_ROLLBACK, OrderLog.SOURCE_UNIONPAY_QUERY);
        orderLog.setRefundOrderId(refundOrderId);
        orderLog.setUnionpayStatus(SysConstants.REFUND_FAIL);
        orderLog.setSuccess(SysConstants.IS_FALSE);
        orderLog.setRemark(remark);
        saveOrderLog(orderLog);
    }

    /**
     * 记录主动退款查询日志。
     */
    private void saveRefundQueryLog(Order order, OrderDetail orderDetail, JSONObject response) {
        OrderLog orderLog = buildOrderLog(order, OrderLog.LOG_TYPE_REFUND,
                OrderLog.ACTION_REFUND_QUERY, OrderLog.SOURCE_USER);
        orderLog.setOrderDetailId(orderDetail.getId());
        orderLog.setAffectedDetailIds(String.valueOf(orderDetail.getId()));
        orderLog.setBeforeOrderStatus(order.getOrderStatus());
        orderLog.setAfterOrderStatus(order.getOrderStatus());
        orderLog.setBeforeDetailStatus(orderDetail.getOrderStatus());
        orderLog.setAfterDetailStatus(orderDetail.getOrderStatus());
        orderLog.setRefundOrderId(orderDetail.getRefundId());
        orderLog.setTradeAmount(orderDetail.getRefundAmount());
        fillUnionPayResponse(orderLog, response);
        orderLog.setSuccess(isUnionSuccessResponse(response) ? SysConstants.IS_TRUE : SysConstants.IS_FALSE);
        orderLog.setRemark("主动查询银联退款状态");
        saveOrderLog(orderLog);
    }

    /**
     * 记录订单放弃支付日志。
     */
    private void saveOrderAbandonLog(Order order, Integer beforeOrderStatus, List<Long> affectedDetailIds) {
        OrderLog orderLog = buildOrderLog(order, OrderLog.LOG_TYPE_ORDER_STATUS,
                OrderLog.ACTION_ORDER_ABANDON, OrderLog.SOURCE_SYSTEM);
        orderLog.setAffectedDetailIds(joinLongIds(affectedDetailIds));
        orderLog.setBeforeOrderStatus(beforeOrderStatus);
        orderLog.setAfterOrderStatus(order.getOrderStatus());
        orderLog.setBeforeDetailStatus(OrderDetail.OrderDetailStatusEnum.INIT.getValue());
        orderLog.setAfterDetailStatus(OrderDetail.OrderDetailStatusEnum.ABANDON.getValue());
        orderLog.setResponseContent("待支付订单已放弃支付");
        orderLog.setSuccess(SysConstants.IS_TRUE);
        orderLog.setRemark("待支付订单超时或主动放弃");
        saveOrderLog(orderLog);
    }

    /**
     * 记录订单核销成功日志。
     */
    private void saveVerificationSuccessLog(Order order, Integer beforeIsUsed) {
        OrderLog orderLog = buildOrderLog(order, OrderLog.LOG_TYPE_VERIFICATION,
                OrderLog.ACTION_VERIFY_ORDER, OrderLog.SOURCE_USER);
        orderLog.setBeforeOrderStatus(order.getOrderStatus());
        orderLog.setAfterOrderStatus(order.getOrderStatus());
        orderLog.setBeforeIsUsed(beforeIsUsed);
        orderLog.setAfterIsUsed(order.getIsUsed());
        orderLog.setSuccess(SysConstants.IS_TRUE);
        orderLog.setRemark("订单核销成功");
        saveOrderLog(orderLog);
    }

    /**
     * 记录订单核销失败日志。
     */
    private void saveVerificationFailLog(Order order, String orderNo, String remark) {
        OrderLog orderLog = ObjectUtil.isEmpty(order)
                ? buildOrderLog(orderNo, OrderLog.LOG_TYPE_VERIFICATION, OrderLog.ACTION_VERIFY_ORDER, OrderLog.SOURCE_USER)
                : buildOrderLog(order, OrderLog.LOG_TYPE_VERIFICATION, OrderLog.ACTION_VERIFY_ORDER, OrderLog.SOURCE_USER);
        orderLog.setBeforeOrderStatus(ObjectUtil.isEmpty(order) ? null : order.getOrderStatus());
        orderLog.setAfterOrderStatus(ObjectUtil.isEmpty(order) ? null : order.getOrderStatus());
        orderLog.setBeforeIsUsed(ObjectUtil.isEmpty(order) ? null : order.getIsUsed());
        orderLog.setAfterIsUsed(ObjectUtil.isEmpty(order) ? null : order.getIsUsed());
        orderLog.setSuccess(SysConstants.IS_FALSE);
        orderLog.setRemark(remark);
        saveOrderLog(orderLog);
    }

    /**
     * 构建带订单快照的日志对象。
     */
    private OrderLog buildOrderLog(Order order, Integer logType, String bizAction, String eventSource) {
        OrderLog orderLog = new OrderLog();
        orderLog.setLogType(logType);
        orderLog.setBizAction(bizAction);
        orderLog.setEventSource(eventSource);
        fillOrderSnapshot(orderLog, order);
        return orderLog;
    }

    /**
     * 构建只有订单号的日志对象。
     */
    private OrderLog buildOrderLog(String orderNo, Integer logType, String bizAction, String eventSource) {
        OrderLog orderLog = new OrderLog();
        orderLog.setOrderNo(orderNo);
        orderLog.setLogType(logType);
        orderLog.setBizAction(bizAction);
        orderLog.setEventSource(eventSource);
        return orderLog;
    }

    /**
     * 填充订单快照字段。
     */
    private void fillOrderSnapshot(OrderLog orderLog, Order order) {
        if (ObjectUtil.isEmpty(order)) {
            return;
        }
        orderLog.setOrderId(order.getId());
        orderLog.setOrderNo(order.getOrderNo());
        orderLog.setMuseumId(order.getMuseumId());
        orderLog.setVisitorId(order.getVisitorId());
        orderLog.setTeamId(order.getTeamId());
        orderLog.setUnionpayOrderNo(order.getUnionpayOrderNo());
    }

    /**
     * 填充银联响应字段。
     */
    private void fillUnionPayResponse(OrderLog orderLog, JSONObject response) {
        if (ObjectUtil.isEmpty(response)) {
            orderLog.setResponseContent(null);
            return;
        }
        orderLog.setResponseContent(response.toString());
        orderLog.setUnionpayErrCode(response.getString("errCode"));
        orderLog.setUnionpayErrMsg(response.getString("errMsg"));
        orderLog.setUnionpayStatus(ObjectUtil.isNotEmpty(response.getString("status"))
                ? response.getString("status") : response.getString("refundStatus"));
        if (ObjectUtil.isNotEmpty(response.getString("targetOrderId"))) {
            orderLog.setUnionpayOrderNo(response.getString("targetOrderId"));
        }
        if (ObjectUtil.isNotEmpty(response.getString("refundOrderId"))) {
            orderLog.setRefundOrderId(response.getString("refundOrderId"));
        }
        Integer tradeAmount = response.getInteger("totalAmount");
        if (ObjectUtil.isEmpty(tradeAmount)) {
            tradeAmount = response.getInteger("refundAmount");
        }
        if (ObjectUtil.isNotEmpty(tradeAmount)) {
            orderLog.setTradeAmount(tradeAmount);
        }
        String tradeTime = ObjectUtil.isNotEmpty(response.getString("payTime"))
                ? response.getString("payTime") : response.getString("refundPayTime");
        if (ObjectUtil.isEmpty(tradeTime)) {
            tradeTime = response.getString("responseTimestamp");
        }
        orderLog.setUnionpayTradeTime(tradeTime);
    }

    /**
     * 保存订单日志。
     */
    private void saveOrderLog(OrderLog orderLog) {
        orderLogService.saveLog(orderLog);
    }

    /**
     * 获取第一条子订单ID。
     */
    private Long firstDetailId(List<OrderDetail> orderDetails) {
        return ObjectUtil.isEmpty(orderDetails) ? null : orderDetails.get(0).getId();
    }

    /**
     * 拼接子订单ID集合。
     */
    private String joinDetailIds(List<OrderDetail> orderDetails) {
        if (ObjectUtil.isEmpty(orderDetails)) {
            return null;
        }
        List<Long> detailIds = new ArrayList<>();
        for (OrderDetail orderDetail : orderDetails) {
            detailIds.add(orderDetail.getId());
        }
        return joinLongIds(detailIds);
    }

    /**
     * 拼接ID集合。
     */
    private String joinLongIds(List<Long> ids) {
        if (ObjectUtil.isEmpty(ids)) {
            return null;
        }
        StringJoiner joiner = new StringJoiner(",");
        for (Long id : ids) {
            if (ObjectUtil.isNotEmpty(id)) {
                joiner.add(String.valueOf(id));
            }
        }
        return joiner.toString();
    }

    /**
     * 限制文本长度，避免异常信息过长。
     */
    private String limitText(String text, int maxLength) {
        if (ObjectUtil.isEmpty(text) || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
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
     * <p>按参数完整性、活动唯一性、游客/团队合法性、博物馆配置、活动金额一致性、场次名额逐步校验；
     * 任一环节失败直接返回前端提示。</p>
     *
     * @param vo 下单参数
     * @return null 表示校验通过，否则返回前端提示
     */
    private String checkAppointment(AppointmentVO vo) {
        // 先校验基础参数，否则后续游客、团队、活动查询没有意义。
        String checkMsg = checkAppointmentParam(vo);
        if (ObjectUtil.isNotEmpty(checkMsg)) {
            return checkMsg;
        }
        // 同一订单内活动必须唯一，多个数量通过 num 表达，不允许拆成多条相同活动明细。
        checkMsg = checkAppointmentActivityUnique(vo);
        if (ObjectUtil.isNotEmpty(checkMsg)) {
            return checkMsg;
        }
        // 有 visitorId 时校验游客维度，包括游客存在性和未支付订单限制。
        checkMsg = checkAppointmentVisitor(vo);
        if (ObjectUtil.isNotEmpty(checkMsg)) {
            return checkMsg;
        }
        // 有 teamId 时校验团队维度，团队和游客二者存在一个即可。
        checkMsg = checkAppointmentTeam(vo);
        if (ObjectUtil.isNotEmpty(checkMsg)) {
            return checkMsg;
        }
        // 博物馆校验放在金额校验前，保证活动归属校验有有效 museumId。
        checkMsg = checkAppointmentMuseum(vo);
        if (ObjectUtil.isNotEmpty(checkMsg)) {
            return checkMsg;
        }
        // 按数据库活动价格重新计算金额，防止前端金额被篡改。
        checkMsg = checkAppointmentAmount(vo);
        if (ObjectUtil.isNotEmpty(checkMsg)) {
            return checkMsg;
        }
        // 最后校验活动场次剩余名额，防止超出场次人数限制。
        return checkAppointmentCapacity(vo);
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
        // 团队订单需要批次号区分同一团队的不同批游客；个人订单没有批次维度，不允许前端误传。
        if (ObjectUtil.isNotEmpty(vo.getTeamId()) && StrUtil.isBlank(vo.getBatchNo())) {
            return "团队下单时游客批次号不能为空";
        }
        if (ObjectUtil.isEmpty(vo.getTeamId()) && StrUtil.isNotBlank(vo.getBatchNo())) {
            return "个人下单时不能传游客批次号";
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
        if (ObjectUtil.isEmpty(vo.getAppointmentDate())) {
            return "预约日期不能为空";
        }
        if (vo.getAppointmentDate().isBefore(LocalDate.now())) {
            return "预约日期不能早于今天";
        }
        if (ObjectUtil.isEmpty(vo.getList())) {
            return "下单详情不能为空";
        }
        return null;
    }

    /**
     * 校验同一订单内活动是否唯一。
     *
     * <p>业务上一个活动可以通过 num 购买多个名额，但同一个 activityManageId
     * 不能在 list 中重复出现，否则金额、余票和子订单拆分都会变得不清晰。</p>
     *
     * @param vo 下单参数
     * @return null 表示活动不重复，否则返回前端提示
     */
    private String checkAppointmentActivityUnique(AppointmentVO vo) {
        Set<Long> activityManageIds = new HashSet<>();
        for (AppointmentVO.AppointmentDetailVO detailVO : vo.getList()) {
            // 明细为空或活动ID为空时，交给后续 checkAppointmentDetail 返回更准确的必填提示。
            if (ObjectUtil.isEmpty(detailVO) || ObjectUtil.isEmpty(detailVO.getActivityManageId())) {
                continue;
            }
            if (!activityManageIds.add(detailVO.getActivityManageId())) {
                return "同一个订单中活动不能重复，请合并活动数量";
            }
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
        if (hasTeamPayingOrder(vo.getTeamId(), vo.getBatchNo())) {
            return "该团队当前批次存在未支付订单，请支付或主动放弃后再下单";
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
     * 判断团队当前游客批次是否已有待支付订单。
     *
     * @param teamId  团队 ID
     * @param batchNo 游客批次号
     * @return true 表示同一团队同一批次存在未支付订单
     */
    private boolean hasTeamPayingOrder(Long teamId, String batchNo) {
        QueryWrapper<Order> orderWrapper = new QueryWrapper<>();
        orderWrapper.eq(Order.TEAM_ID, teamId);
        // 同一团队可能多批次出行，未支付订单限制只约束当前批次。
        orderWrapper.eq(Order.BATCH_NO, batchNo.trim());
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
            // 每条下单明细先校验活动合法性，再参与金额汇总。
            String detailMsg = checkAppointmentDetail(vo, detailVO);
            if (ObjectUtil.isNotEmpty(detailMsg)) {
                return detailMsg;
            }
            ActivityManage activityManage = activityManageService.getById(detailVO.getActivityManageId());
            totalAmount += activityManage.getPrice() * detailVO.getNum();
        }
        // 前端传入金额必须等于数据库重新计算金额，最终以数据库价格为准。
        if (!vo.getMoney().equals(totalAmount)) {
            return "支付金额与活动总金额不一致";
        }
        return null;
    }

    /**
     * 校验活动场次剩余名额是否足够。
     *
     * <p>同一个下单请求可能重复传入同一活动、同一场次，因此先聚合本次请求数量；
     * 数据库侧统计待支付、支付成功、退款中的子订单，这些订单都会临时或实际占用名额。</p>
     *
     * @param vo 下单参数
     * @return null 表示名额足够，否则返回前端提示
     */
    private String checkAppointmentCapacity(AppointmentVO vo) {
        // requestQuantityMap：统计本次请求里同一活动、同一场次一共要购买多少张。
        Map<String, Integer> requestQuantityMap = new LinkedHashMap<>();
        // detailMap：保存每个活动场次的一条原始明细，后面查询已预约人数时复用活动ID和场次ID。
        Map<String, AppointmentVO.AppointmentDetailVO> detailMap = new HashMap<>();
        // scheduleMap：保存每个活动场次的数据库配置，后面读取 scheduleNumber 做容量判断。
        Map<String, ActivitySchedule> scheduleMap = new HashMap<>();
        for (AppointmentVO.AppointmentDetailVO detailVO : vo.getList()) {
            String capacityKey = buildScheduleCapacityKey(detailVO.getActivityManageId(), detailVO.getActivityScheduleId());
            // 同一单内重复选择同一活动场次时，需要合并数量，避免拆成多条明细绕过人数限制。
            requestQuantityMap.put(capacityKey, requestQuantityMap.getOrDefault(capacityKey, 0) + detailVO.getNum());
            detailMap.putIfAbsent(capacityKey, detailVO);

            // 查询场次配置，拿到当前活动场次允许预约的人数上限。
            ActivitySchedule activitySchedule = activityScheduleService.getById(detailVO.getActivityScheduleId());
            if (ObjectUtil.isEmpty(activitySchedule)) {
                return "活动场次不存在";
            }
            if (!SysConstants.IS_TRUE.equals(activitySchedule.getStatus())) {
                return "活动场次已禁用";
            }
            if (ObjectUtil.isEmpty(activitySchedule.getScheduleNumber()) || activitySchedule.getScheduleNumber() <= 0) {
                return "活动场次人数配置有误";
            }
            // 同一个活动场次只保留一份配置，避免重复查询结果覆盖无意义。
            scheduleMap.putIfAbsent(capacityKey, activitySchedule);
        }

        // 遍历聚合后的活动场次，逐个判断“已预约人数 + 本次购买人数”是否超出上限。
        for (Map.Entry<String, Integer> entry : requestQuantityMap.entrySet()) {
            AppointmentVO.AppointmentDetailVO detailVO = detailMap.get(entry.getKey());
            ActivitySchedule activitySchedule = scheduleMap.get(entry.getKey());
            // 查询数据库里当前日期、活动、场次已经占用的子订单数量。
            int bookedQuantity = orderDetailService.countBookedQuantity(vo.getMuseumId(), detailVO.getActivityManageId(),
                    detailVO.getActivityScheduleId(), vo.getAppointmentDate());
            // 已预约人数包含待支付订单，待支付订单 15 分钟超时释放后才不再占用名额。
            int remainingQuantity = activitySchedule.getScheduleNumber() - bookedQuantity;
            if (entry.getValue() > remainingQuantity) {
                return "活动场次余票不足，剩余名额" + Math.max(remainingQuantity, 0) + "人";
            }
        }
        return null;
    }

    /**
     * 构建活动场次容量校验用的聚合键。
     *
     * @param activityId         活动ID
     * @param activityScheduleId 活动场次ID
     * @return 聚合键
     */
    private String buildScheduleCapacityKey(Long activityId, Long activityScheduleId) {
        return activityId + ":" + activityScheduleId;
    }

    /**
     * 校验单条活动下单明细。
     *
     * @param vo       下单参数
     * @param detailVO 活动明细
     * @return null 表示明细校验通过，否则返回前端提示
     */
    private String checkAppointmentDetail(AppointmentVO vo, AppointmentVO.AppointmentDetailVO detailVO) {
        // 先校验明细必填项，保证后续查询活动和场次时不会出现空指针。
        if (ObjectUtil.isEmpty(detailVO) || ObjectUtil.isEmpty(detailVO.getActivityManageId())) {
            return "活动ID不能为空";
        }
        if (ObjectUtil.isEmpty(detailVO.getActivityScheduleId())) {
            return "活动场次ID不能为空";
        }
        if (ObjectUtil.isEmpty(detailVO.getNum()) || detailVO.getNum() <= 0) {
            return "活动数量不能为空且必须大于0";
        }
        // 查询活动主表，校验活动本身是否可售、是否属于当前博物馆、价格是否正常。
        ActivityManage activityManage = activityManageService.getById(detailVO.getActivityManageId());
        if (ObjectUtil.isEmpty(activityManage) || SysConstants.IS_TRUE.equals(activityManage.getIsDeleted())) {
            return "活动不存在或已删除";
        }
        if (ObjectUtil.isEmpty(activityManage.getActivityStartDate()) || ObjectUtil.isEmpty(activityManage.getActivityEndDate())
                || activityManage.getActivityStartDate().isAfter(activityManage.getActivityEndDate())) {
            return "活动日期配置有误";
        }
        // 预约日期以主订单 appointmentDate 为准，必须落在活动开放日期范围内。
        if (vo.getAppointmentDate().isBefore(activityManage.getActivityStartDate())
                || vo.getAppointmentDate().isAfter(activityManage.getActivityEndDate())) {
            return "预约日期不在活动开放日期范围内";
        }
        // 场次必须存在，并且必须挂在当前活动下，避免前端把其他活动的场次混传进来。
        ActivitySchedule activitySchedule = activityScheduleService.getById(detailVO.getActivityScheduleId());
        if (ObjectUtil.isEmpty(activitySchedule)) {
            return "活动场次不存在";
        }
        if (!detailVO.getActivityManageId().equals(activitySchedule.getActivityId())) {
            return "活动场次不属于当前活动";
        }
        if (!SysConstants.IS_TRUE.equals(activitySchedule.getStatus())) {
            return "活动场次已禁用";
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
