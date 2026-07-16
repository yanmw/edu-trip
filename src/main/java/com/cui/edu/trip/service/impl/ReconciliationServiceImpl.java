package com.cui.edu.trip.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cui.edu.common.HttpStatus;
import com.cui.edu.config.exception.MyException;
import com.cui.edu.system.entity.Museum;
import com.cui.edu.system.service.MuseumService;
import com.cui.edu.trip.entity.ActivityManage;
import com.cui.edu.trip.entity.Order;
import com.cui.edu.trip.entity.OrderDetail;
import com.cui.edu.trip.entity.unionpay.UnionPayData;
import com.cui.edu.trip.mapper.ActivityManageMapper;
import com.cui.edu.trip.mapper.OrderDetailMapper;
import com.cui.edu.trip.mapper.OrderMapper;
import com.cui.edu.trip.mapper.UnionPayDataMapper;
import com.cui.edu.trip.service.ReconciliationService;
import com.cui.edu.vo.reconciliation.ReconciliationAbnormalQueryVO;
import com.cui.edu.vo.reconciliation.ReconciliationAbnormalResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ReconciliationServiceImpl implements ReconciliationService {

    private static final String TYPE_PAY = "消费";
    private static final String TYPE_REFUND = "联机退货";
    private static final int QUERY_BATCH_SIZE = 500;

    private static final String GROUP_PAYMENT = "PAYMENT_VERIFICATION";
    private static final String GROUP_REFUND = "REFUND";
    private static final String GROUP_DATA = "DATA_INTEGRITY";
    private static final String GROUP_IMPORT = "IMPORT_QUALITY";

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private UnionPayDataMapper unionPayDataMapper;
    @Autowired
    private ActivityManageMapper activityManageMapper;
    @Autowired
    private MuseumService museumService;

    @Override
    public ReconciliationAbnormalResult findAbnormalData(ReconciliationAbnormalQueryVO vo) {
        // 将包含结束日的查询条件转换为左闭右开区间，例如1月1日至1月18日转为[1月1日, 1月19日)。
        QueryPeriod period = validateAndBuildPeriod(vo);
        // 博物馆上的mid、tid决定本次应核对哪一个银联商户终端的流水。
        Museum museum = museumService.getById(vo.getMuseumId());
        if (museum == null) {
            throw new MyException(HttpStatus.SC_BAD_REQUEST, "博物馆不存在");
        }
        if (StrUtil.isBlank(museum.getMid()) || StrUtil.isBlank(museum.getTid())) {
            throw new MyException(HttpStatus.SC_BAD_REQUEST, "博物馆未配置银联商户号或终端号");
        }

        // 退款发生在子订单上，先单独查出核对期退款，避免遗漏历史下单后核对期退款的订单。
        List<OrderDetail> currentRefundDetails = findCurrentRefundDetails(museum.getId(), period);
        // 通过退款子订单反向补齐主订单ID，后面与支付、核销、预约条件一起查询。
        Set<Long> refundOrderIds = currentRefundDetails.stream()
                .map(OrderDetail::getOrderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // 种子订单包含：核对期支付、核对期核销、预约日在核对期、核对期发生退款的订单。
        List<Order> orders = findSeedOrders(museum.getId(), period, refundOrderIds);
        // 银联只按交易时间查询核对区间内的消费和退款流水。
        List<UnionPayData> currentUnionRows = findCurrentUnionRows(museum, period);

        // 银联核对期存在而种子订单中不存在的交易号，可能是系统漏单，必须反查系统订单。
        Set<String> currentUnionTradeNos = currentUnionRows.stream()
                .map(UnionPayData::getMerchantOrderNo)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> loadedTradeNos = orders.stream()
                .map(Order::getUnionpayOrderNo)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet());
        // 只查询尚未加载的交易号，减少重复SQL。
        currentUnionTradeNos.removeAll(loadedTradeNos);
        orders.addAll(findOrdersByTradeNos(museum.getId(), currentUnionTradeNos));
        // 同一个订单可能同时命中多个种子条件，按系统订单ID去重。
        orders = deduplicateOrders(orders);

        // 收集系统和银联两侧出现过的全部交易号，用于补查跨月支付、退款历史。
        Set<String> allTradeNos = new LinkedHashSet<>();
        for (Order order : orders) {
            if (StrUtil.isNotBlank(order.getUnionpayOrderNo())) {
                allTradeNos.add(order.getUnionpayOrderNo());
            }
        }
        for (UnionPayData row : currentUnionRows) {
            if (StrUtil.isNotBlank(row.getMerchantOrderNo())) {
                allTradeNos.add(row.getMerchantOrderNo());
            }
        }

        // 不限制日期查询候选交易号的全部银联流水，以判断区间外支付和跨期退款。
        List<UnionPayData> historicalUnionRows = findUnionRowsByTradeNos(museum, allTradeNos);
        // 查询候选主订单的全部子订单，核销有效金额及累计退款都以子订单为准。
        List<OrderDetail> allOrderDetails = findOrderDetails(orders);
        // 批量查询子订单关联的活动，替代旧详情接口中过时的product表关联。
        Map<Long, ActivityManage> activityMap = findActivities(allOrderDetails);
        // unionpay_order_no与merchant_order_no相同，以该值组装一份订单级对账台账。
        LinkedHashMap<String, Ledger> ledgers = buildLedgers(orders, allOrderDetails, currentUnionRows,
                historicalUnionRows, activityMap);
        // 参考号重复属于银联导入质量问题，不影响多笔合法退款按交易号汇总。
        Set<String> duplicatedReferences = findDuplicatedReferences(currentUnionRows);

        // 初始化固定分类；没有异常数据的分类也会返回空details，便于前端稳定展示。
        ReconciliationAbnormalResult result = initializeResult(vo);
        Map<String, ReconciliationAbnormalResult.AbnormalCategory> categoryMap = indexCategories(result);
        Map<String, ReconciliationAbnormalResult.BillingDetail> billingMap = new LinkedHashMap<>();
        long unclassifiedCount = 0L;

        for (Ledger ledger : ledgers.values()) {
            // 先将当前订单的系统核销、系统退款、银联支付和银联退款计算为统一指标。
            LedgerMetrics metrics = calculateMetrics(ledger, period);
            // 同一批已加载数据同步生成活动维度的有效核销账单，替代旧/billing查询。
            accumulateBilling(billingMap, ledger, period);
            // 同一订单允许命中多个异常标签，例如“区间前支付、核对期核销”同时伴随退款异常。
            List<AnomalyHit> hits = classify(ledger, metrics, period, duplicatedReferences);
            // 单笔调整额恒等于：系统核对期有效核销金额 - 银联核对期净额。
            long adjustment = metrics.systemValidAmount - metrics.unionNetAmount;
            if (hits.isEmpty() && adjustment != 0L) {
                // 只要金额不闭环，就不能静默忽略；未知情况统一进入兜底异常。
                hits.add(new AnomalyHit("UNCLASSIFIED_BALANCE_DIFFERENCE",
                        "该订单存在余额差异，但尚未命中已知异常规则"));
                unclassifiedCount++;
            }
            if (!hits.isEmpty()) {
                // 多标签订单只让一个主分类承担调整额，防止同一差额被重复加减。
                String adjustmentOwner = chooseAdjustmentOwner(hits);
                addHitsToCategories(categoryMap, ledger, metrics, hits, adjustmentOwner, adjustment);
            }
            // 无论是否异常，每个订单台账都必须进入总账统计。
            accumulateBalance(result.getBalance(), metrics);
        }

        // 完成分类数量、数据覆盖统计以及最终加减闭环校验。
        finishBilling(result, billingMap);
        finishCategories(result);
        finishSourceControl(result, currentRefundDetails, currentUnionRows, unclassifiedCount);
        finishBalance(result);
        return result;
    }

    private QueryPeriod validateAndBuildPeriod(ReconciliationAbnormalQueryVO vo) {
        if (vo == null || StrUtil.isBlank(vo.getMuseumId())
                || vo.getStartDate() == null || vo.getEndDate() == null) {
            throw new MyException(HttpStatus.SC_BAD_REQUEST, "博物馆ID、核对开始日期和结束日期不能为空");
        }
        if (vo.getEndDate().isBefore(vo.getStartDate())) {
            throw new MyException(HttpStatus.SC_BAD_REQUEST, "核对结束日期不能早于开始日期");
        }
        LocalDate startDate = vo.getStartDate();
        // 用户传入的结束日期包含当天，因此内部以上述日期的次日作为排他上界。
        LocalDate endDateExclusive = vo.getEndDate().plusDays(1);
        return new QueryPeriod(startDate, endDateExclusive, startDate.atStartOfDay(),
                endDateExclusive.atStartOfDay());
    }

    private List<OrderDetail> findCurrentRefundDetails(Long museumId, QueryPeriod period) {
        QueryWrapper<OrderDetail> wrapper = new QueryWrapper<>();
        // 只认可退款成功状态且refund_time落在核对区间内的子订单。
        wrapper.eq(OrderDetail.MUSEUM_ID, museumId);
        wrapper.eq(OrderDetail.ORDER_STATUS, OrderDetail.OrderDetailStatusEnum.REFUND.getValue());
        wrapper.ge(OrderDetail.REFUND_TIME, period.startDateTime);
        wrapper.lt(OrderDetail.REFUND_TIME, period.endDateTimeExclusive);
        return orderDetailMapper.selectList(wrapper);
    }

    private List<Order> findSeedOrders(Long museumId, QueryPeriod period, Set<Long> refundOrderIds) {
        QueryWrapper<Order> wrapper = new QueryWrapper<>();
        wrapper.eq(Order.MUSEUM_ID, museumId);
        // 四类时间事件用OR连接，保证跨月支付、预约、核销、退款都能进入候选集。
        wrapper.and(condition -> {
            condition.ge(Order.PAY_SUCCESS_TIME, period.startDateTime)
                    .lt(Order.PAY_SUCCESS_TIME, period.endDateTimeExclusive)
                    .or()
                    .ge(Order.VERIFICATION_TIME, period.startDateTime)
                    .lt(Order.VERIFICATION_TIME, period.endDateTimeExclusive)
                    .or()
                    .ge(Order.APPOINTMENT_DATE, period.startDate)
                    .lt(Order.APPOINTMENT_DATE, period.endDateExclusive);
            if (!refundOrderIds.isEmpty()) {
                condition.or().in(Order.ID, refundOrderIds);
            }
        });
        return orderMapper.selectList(wrapper);
    }

    private List<UnionPayData> findCurrentUnionRows(Museum museum, QueryPeriod period) {
        QueryWrapper<UnionPayData> wrapper = new QueryWrapper<>();
        // mid和tid必须同时一致，防止同一商户或同一终端下的其他博物馆流水混入。
        wrapper.eq(UnionPayData.MID, museum.getMid());
        wrapper.eq(UnionPayData.TID, museum.getTid());
        wrapper.ge(UnionPayData.TRANSACTION_TIME, period.startDateTime);
        wrapper.lt(UnionPayData.TRANSACTION_TIME, period.endDateTimeExclusive);
        return unionPayDataMapper.selectList(wrapper);
    }

    private List<Order> findOrdersByTradeNos(Long museumId, Collection<String> tradeNos) {
        if (tradeNos.isEmpty()) {
            return new ArrayList<>();
        }
        List<Order> result = new ArrayList<>();
        List<String> values = new ArrayList<>(tradeNos);
        // 分批拼接IN条件，避免交易号过多导致SQL参数超限。
        for (int from = 0; from < values.size(); from += QUERY_BATCH_SIZE) {
            List<String> batch = values.subList(from, Math.min(from + QUERY_BATCH_SIZE, values.size()));
            QueryWrapper<Order> wrapper = new QueryWrapper<>();
            wrapper.eq(Order.MUSEUM_ID, museumId);
            wrapper.in(Order.UNIONPAY_ORDER_NO, batch);
            result.addAll(orderMapper.selectList(wrapper));
        }
        return result;
    }

    private List<UnionPayData> findUnionRowsByTradeNos(Museum museum, Collection<String> tradeNos) {
        if (tradeNos.isEmpty()) {
            return new ArrayList<>();
        }
        List<UnionPayData> result = new ArrayList<>();
        List<String> values = new ArrayList<>(tradeNos);
        // 这里不加日期条件，因为需要知道支付或退款是否发生在核对区间外。
        for (int from = 0; from < values.size(); from += QUERY_BATCH_SIZE) {
            List<String> batch = values.subList(from, Math.min(from + QUERY_BATCH_SIZE, values.size()));
            QueryWrapper<UnionPayData> wrapper = new QueryWrapper<>();
            wrapper.eq(UnionPayData.MID, museum.getMid());
            wrapper.eq(UnionPayData.TID, museum.getTid());
            wrapper.in(UnionPayData.MERCHANT_ORDER_NO, batch);
            result.addAll(unionPayDataMapper.selectList(wrapper));
        }
        return deduplicateUnionRows(result);
    }

    private List<OrderDetail> findOrderDetails(List<Order> orders) {
        List<Long> orderIds = orders.stream().map(Order::getId).filter(Objects::nonNull).distinct()
                .collect(Collectors.toList());
        if (orderIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<OrderDetail> result = new ArrayList<>();
        // 查询全部子订单，而不是只查核对期子订单；后续才能计算有效核销和累计退款。
        for (int from = 0; from < orderIds.size(); from += QUERY_BATCH_SIZE) {
            List<Long> batch = orderIds.subList(from, Math.min(from + QUERY_BATCH_SIZE, orderIds.size()));
            QueryWrapper<OrderDetail> wrapper = new QueryWrapper<>();
            wrapper.in(OrderDetail.ORDER_ID, batch);
            result.addAll(orderDetailMapper.selectList(wrapper));
        }
        return result;
    }

    private Map<Long, ActivityManage> findActivities(List<OrderDetail> details) {
        List<Long> activityIds = details.stream()
                .map(OrderDetail::getActivityId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (activityIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, ActivityManage> result = new HashMap<>();
        for (int from = 0; from < activityIds.size(); from += QUERY_BATCH_SIZE) {
            List<Long> batch = activityIds.subList(from, Math.min(from + QUERY_BATCH_SIZE, activityIds.size()));
            QueryWrapper<ActivityManage> wrapper = new QueryWrapper<>();
            wrapper.in(ActivityManage.ID, batch);
            for (ActivityManage activity : activityManageMapper.selectList(wrapper)) {
                if (activity != null && activity.getId() != null) {
                    result.put(activity.getId(), activity);
                }
            }
        }
        return result;
    }

    private List<Order> deduplicateOrders(List<Order> orders) {
        LinkedHashMap<Long, Order> result = new LinkedHashMap<>();
        for (Order order : orders) {
            if (order != null && order.getId() != null) {
                result.put(order.getId(), order);
            }
        }
        return new ArrayList<>(result.values());
    }

    private List<UnionPayData> deduplicateUnionRows(List<UnionPayData> rows) {
        LinkedHashMap<String, UnionPayData> result = new LinkedHashMap<>();
        int nullIdIndex = 0;
        for (UnionPayData row : rows) {
            String key = row.getId() == null ? "NULL#" + nullIdIndex++ : String.valueOf(row.getId());
            result.put(key, row);
        }
        return new ArrayList<>(result.values());
    }

    private LinkedHashMap<String, Ledger> buildLedgers(List<Order> orders, List<OrderDetail> details,
                                                        List<UnionPayData> currentUnionRows,
                                                        List<UnionPayData> historicalUnionRows,
                                                        Map<Long, ActivityManage> activityMap) {
        LinkedHashMap<String, Ledger> result = new LinkedHashMap<>();
        // 先按主订单ID归集子订单，再挂入以银联交易号为键的对账台账。
        Map<Long, List<OrderDetail>> detailMap = details.stream().filter(item -> item.getOrderId() != null)
                .collect(Collectors.groupingBy(OrderDetail::getOrderId));
        for (Order order : orders) {
            // 缺少银联交易号时使用系统订单ID生成临时键，确保脏数据也不会被丢弃。
            String key = StrUtil.isBlank(order.getUnionpayOrderNo()) ? "SYSTEM_ORDER#" + order.getId()
                    : "TRADE#" + order.getUnionpayOrderNo();
            Ledger ledger = result.computeIfAbsent(key, value -> new Ledger(order.getUnionpayOrderNo()));
            ledger.orders.add(order);
            ledger.details.addAll(detailMap.getOrDefault(order.getId(), Collections.emptyList()));
            ledger.activities.putAll(activityMap);
        }
        for (UnionPayData row : currentUnionRows) {
            // 银联缺少merchant_order_no时按流水ID单独建账，随后归入导入质量异常。
            String key = StrUtil.isBlank(row.getMerchantOrderNo()) ? "UNION_ROW#" + row.getId()
                    : "TRADE#" + row.getMerchantOrderNo();
            Ledger ledger = result.computeIfAbsent(key, value -> new Ledger(row.getMerchantOrderNo()));
            ledger.currentUnionRows.add(row);
        }
        // 历史流水只挂到已有候选台账，不额外扩散查询范围。
        for (UnionPayData row : historicalUnionRows) {
            if (StrUtil.isBlank(row.getMerchantOrderNo())) {
                continue;
            }
            Ledger ledger = result.get("TRADE#" + row.getMerchantOrderNo());
            if (ledger != null) {
                ledger.allUnionRows.add(row);
            }
        }
        for (Ledger ledger : result.values()) {
            ledger.orders.sort(Comparator.comparing(Order::getId, Comparator.nullsLast(Long::compareTo)));
            ledger.details.sort(Comparator.comparing(OrderDetail::getId, Comparator.nullsLast(Long::compareTo)));
            ledger.currentUnionRows.sort(Comparator.comparing(UnionPayData::getId,
                    Comparator.nullsLast(Long::compareTo)));
        }
        return result;
    }

    private Set<String> findDuplicatedReferences(List<UnionPayData> rows) {
        // 同订单可以有多笔退款，但相同银联参考号重复出现通常代表重复导入。
        Map<String, Long> counts = rows.stream().map(UnionPayData::getReferenceNumber)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.groupingBy(value -> value, Collectors.counting()));
        return counts.entrySet().stream().filter(entry -> entry.getValue() > 1L).map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private LedgerMetrics calculateMetrics(Ledger ledger, QueryPeriod period) {
        LedgerMetrics metrics = new LedgerMetrics();
        // 正常应只有一个系统订单；若重复，取ID最小的订单作为详情快照，同时另报重复异常。
        metrics.primaryOrder = ledger.orders.isEmpty() ? null : ledger.orders.get(0);
        // 有效核销必须同时满足：主订单已核销，且verification_time位于核对区间。
        Set<Long> verifiedOrderIds = ledger.orders.stream()
                .filter(order -> Integer.valueOf(1).equals(order.getIsUsed()))
                .filter(order -> inPeriod(order.getVerificationTime(), period))
                .map(Order::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        for (OrderDetail detail : ledger.details) {
            // 已退款子订单状态不是PAY_SUCCESS，因此不计入核对期有效核销数量和金额。
            if (verifiedOrderIds.contains(detail.getOrderId())
                    && OrderDetail.OrderDetailStatusEnum.PAY_SUCCESS.getValue().equals(detail.getOrderStatus())) {
                metrics.systemValidQuantity++;
                metrics.systemValidAmount += safeLong(detail.getOrderAmount());
            }
            // 系统退款允许一笔或多笔，按退款成功子订单逐笔累计。
            if (OrderDetail.OrderDetailStatusEnum.REFUND.getValue().equals(detail.getOrderStatus())) {
                long refundAmount = safeLong(detail.getRefundAmount());
                metrics.systemCumulativeRefundAmount += refundAmount;
                if (inPeriod(detail.getRefundTime(), period)) {
                    metrics.currentSystemRefundDetails.add(detail);
                    metrics.systemCurrentRefundAmount += refundAmount;
                } else if (detail.getRefundTime() != null) {
                    metrics.hasHistoricalSystemRefund = true;
                }
            }
        }
        // 银联同交易号正常只有一条消费，但退款可能存在多条，分别保留明细并汇总金额。
        metrics.currentPayments = ledger.currentUnionRows.stream().filter(row -> TYPE_PAY.equals(row.getType()))
                .collect(Collectors.toList());
        metrics.currentRefunds = ledger.currentUnionRows.stream().filter(row -> TYPE_REFUND.equals(row.getType()))
                .collect(Collectors.toList());
        metrics.currentUnsupportedRows = ledger.currentUnionRows.stream()
                .filter(row -> !TYPE_PAY.equals(row.getType()) && !TYPE_REFUND.equals(row.getType()))
                .collect(Collectors.toList());
        // 从全量流水中剔除核对区间，剩余部分用于判断区间外支付和跨期退款。
        metrics.historicalPayments = ledger.allUnionRows.stream().filter(row -> TYPE_PAY.equals(row.getType()))
                .filter(row -> !inPeriod(row.getTransactionTime(), period)).collect(Collectors.toList());
        metrics.historicalRefunds = ledger.allUnionRows.stream().filter(row -> TYPE_REFUND.equals(row.getType()))
                .filter(row -> !inPeriod(row.getTransactionTime(), period)).collect(Collectors.toList());
        metrics.allPayments = ledger.allUnionRows.stream().filter(row -> TYPE_PAY.equals(row.getType()))
                .collect(Collectors.toList());
        metrics.allRefunds = ledger.allUnionRows.stream().filter(row -> TYPE_REFUND.equals(row.getType()))
                .collect(Collectors.toList());
        metrics.unionCurrentPayAmount = sumUnionAmount(metrics.currentPayments);
        metrics.unionCurrentRefundAmount = sumUnionAmount(metrics.currentRefunds);
        metrics.unionHistoricalPayAmount = sumUnionAmount(metrics.historicalPayments);
        metrics.unionHistoricalRefundAmount = sumUnionAmount(metrics.historicalRefunds);
        metrics.unionCumulativeRefundAmount = sumUnionAmount(metrics.allRefunds);
        // 银联核对期净额固定为核对期消费减核对期退款。
        metrics.unionNetAmount = metrics.unionCurrentPayAmount - metrics.unionCurrentRefundAmount;
        metrics.hasCurrentVerification = ledger.orders.stream()
                .anyMatch(order -> Integer.valueOf(1).equals(order.getIsUsed())
                        && inPeriod(order.getVerificationTime(), period));
        metrics.hasCurrentSystemPay = ledger.orders.stream().anyMatch(order -> inPeriod(order.getPaySuccessTime(), period));
        metrics.hasHistoricalSystemPayOrVerification = ledger.orders.stream().anyMatch(order ->
                before(order.getPaySuccessTime(), period.startDateTime)
                        || before(order.getVerificationTime(), period.startDateTime));
        return metrics;
    }

    private List<AnomalyHit> classify(Ledger ledger, LedgerMetrics metrics, QueryPeriod period,
                                      Set<String> duplicatedReferences) {
        // LinkedHashMap既可去重异常编码，也能保持固定的返回顺序。
        LinkedHashMap<String, AnomalyHit> hits = new LinkedHashMap<>();
        Order order = metrics.primaryOrder;

        // 第一层：先检查两侧关联键及重复数据，防止基础数据问题被金额规则掩盖。
        if (!ledger.orders.isEmpty() && StrUtil.isBlank(ledger.tradeNo)) {
            putHit(hits, "SYSTEM_TRADE_NO_MISSING", "系统订单缺少unionpay_order_no，无法与银联流水关联");
        }
        if (!ledger.currentUnionRows.isEmpty() && StrUtil.isBlank(ledger.tradeNo)) {
            putHit(hits, "UNION_TRADE_NO_MISSING", "银联流水缺少merchant_order_no，无法与系统订单关联");
        }
        if (ledger.orders.size() > 1) {
            putHit(hits, "DUPLICATE_SYSTEM_TRADE_NO", "多个系统订单使用了同一个unionpay_order_no");
        }
        if (metrics.currentPayments.size() > 1) {
            putHit(hits, "DUPLICATE_UNION_PAYMENT", "同一merchant_order_no在核对期出现多条消费流水");
        }
        if (hasInvalidUnionAmount(metrics.currentPayments, false)
                || hasInvalidUnionAmount(metrics.currentRefunds, true)) {
            putHit(hits, "UNION_AMOUNT_INVALID", "银联流水金额为空或为0，无法正常核对");
        }
        if (!metrics.currentUnsupportedRows.isEmpty()) {
            putHit(hits, "UNSUPPORTED_UNION_TYPE", "核对期存在未识别的银联交易类型");
        }
        boolean duplicatedReference = ledger.currentUnionRows.stream().map(UnionPayData::getReferenceNumber)
                .filter(StrUtil::isNotBlank).anyMatch(duplicatedReferences::contains);
        if (duplicatedReference) {
            putHit(hits, "DUPLICATE_UNION_REFERENCE", "核对期存在重复的银联参考号");
        }

        // 第二层：检查系统主订单、子订单的核销和退款状态是否自洽。
        for (Order current : ledger.orders) {
            if ((Integer.valueOf(1).equals(current.getIsUsed()) && current.getVerificationTime() == null)
                    || (!Integer.valueOf(1).equals(current.getIsUsed()) && current.getVerificationTime() != null)) {
                putHit(hits, "INVALID_VERIFICATION_STATUS", "系统订单核销状态与核销时间不一致");
            }
            // 主订单refund_amount必须等于所有退款成功子订单的退款金额之和。
            long detailRefundAmount = ledger.details.stream()
                    .filter(detail -> Objects.equals(current.getId(), detail.getOrderId()))
                    .filter(detail -> OrderDetail.OrderDetailStatusEnum.REFUND.getValue()
                            .equals(detail.getOrderStatus()))
                    .mapToLong(detail -> safeLong(detail.getRefundAmount())).sum();
            if (safeLong(current.getRefundAmount()) != detailRefundAmount) {
                putHit(hits, "ORDER_REFUND_SUM_MISMATCH", "主订单退款金额与已退款子订单合计不一致");
            }
            if (detailRefundAmount > safeLong(current.getPayAmount())) {
                putHit(hits, "OVER_REFUND", "系统累计退款金额超过订单支付金额");
            }
        }
        boolean invalidRefundDetail = ledger.details.stream().anyMatch(detail ->
                (OrderDetail.OrderDetailStatusEnum.REFUND.getValue().equals(detail.getOrderStatus())
                        && detail.getRefundTime() == null)
                        || (detail.getRefundTime() != null
                        && !OrderDetail.OrderDetailStatusEnum.REFUND.getValue().equals(detail.getOrderStatus())));
        if (invalidRefundDetail) {
            putHit(hits, "INVALID_REFUND_STATUS", "子订单退款状态与退款时间不一致");
        }

        // 第三层：以银联核对期消费为入口，判断系统是否在同一核对区间形成有效核销。
        if (!metrics.currentPayments.isEmpty()) {
            if (ledger.orders.isEmpty()) {
                putHit(hits, "UNION_PAYMENT_ORDER_NOT_FOUND", "银联核对期有支付流水，但系统订单不存在");
            } else if (!metrics.hasCurrentVerification) {
                // 预约日在核对结束日以后，属于“核对期支付、跨期预约”。
                boolean futureAppointment = ledger.orders.stream().map(Order::getAppointmentDate)
                        .filter(Objects::nonNull).anyMatch(date -> !date.isBefore(period.endDateExclusive));
                boolean dueAppointment = ledger.orders.stream().map(Order::getAppointmentDate)
                        .filter(Objects::nonNull).anyMatch(date -> date.isBefore(period.endDateExclusive));
                if (futureAppointment) {
                    putHit(hits, "CROSS_MONTH_APPOINTMENT", "订单在核对期支付，但预约日期在核对区间之后");
                } else if (dueAppointment) {
                    putHit(hits, "APPOINTMENT_NOT_VERIFIED", "订单预约日期已到，但核对期没有核销");
                } else {
                    putHit(hits, "UNION_PAYMENT_WITHOUT_CURRENT_VERIFICATION",
                            "银联核对期有支付流水，但系统核对期没有有效核销");
                }
            }
        }
        // 系统核对期显示支付成功，但银联核对期没有消费流水，属于单边支付数据。
        if (metrics.hasCurrentSystemPay && metrics.currentPayments.isEmpty()) {
            putHit(hits, "SYSTEM_PAYMENT_UNION_MISSING", "系统核对期支付成功，但银联核对期没有消费流水");
        }
        // 系统核对期核销而银联核对期无支付时，继续区分区间外支付和完全无支付流水。
        if (metrics.hasCurrentVerification && metrics.currentPayments.isEmpty()) {
            if (!metrics.historicalPayments.isEmpty()) {
                putHit(hits, "HISTORICAL_PAY_CURRENT_VERIFICATION", "订单在核对区间前支付，核对期完成核销");
            } else {
                putHit(hits, "VERIFICATION_WITHOUT_ANY_PAYMENT", "系统核对期有核销，但银联历史账单无支付流水");
            }
        }
        // 即使银联核对期没有消费，也要捕获“区间前已支付、核对期预约到期却未核销”。
        boolean appointmentDueWithoutVerification = ledger.orders.stream().anyMatch(current ->
                current.getAppointmentDate() != null
                        && !current.getAppointmentDate().isBefore(period.startDate)
                        && current.getAppointmentDate().isBefore(period.endDateExclusive)
                        && !inPeriod(current.getVerificationTime(), period)
                        && current.getPaySuccessTime() != null
                        && current.getPaySuccessTime().isBefore(period.endDateTimeExclusive));
        if (appointmentDueWithoutVerification) {
            putHit(hits, "APPOINTMENT_NOT_VERIFIED", "预约日期在核对期且已支付，但核对期没有核销");
        }

        // 支付金额比较使用系统原始支付金额与银联全历史消费金额，不使用退款后的有效金额。
        if (order != null && !metrics.allPayments.isEmpty()) {
            long systemPayAmount = safeLong(order.getPayAmount());
            long unionPayAmount = sumUnionAmount(metrics.allPayments);
            if (systemPayAmount != unionPayAmount) {
                putHit(hits, "PAY_AMOUNT_MISMATCH", "系统支付金额与银联消费流水金额不一致");
            }
        }
        if (order != null && metrics.unionCumulativeRefundAmount > safeLong(order.getPayAmount())) {
            putHit(hits, "OVER_REFUND", "银联累计退款金额超过原消费金额");
        }

        // 第四层：系统与银联退款均按核对区间汇总，同一交易号允许一对多退款流水。
        boolean systemRefund = !metrics.currentSystemRefundDetails.isEmpty();
        boolean unionRefund = !metrics.currentRefunds.isEmpty();
        if (systemRefund && !unionRefund) {
            if (!metrics.historicalRefunds.isEmpty()) {
                putHit(hits, "REFUND_MONTH_MISMATCH", "系统退款发生在核对期，但银联退款记录在核对区间外");
            } else {
                putHit(hits, "SYSTEM_REFUND_ONLY", "系统核对期有退款成功记录，但银联核对期无退款流水");
            }
        } else if (!systemRefund && unionRefund) {
            if (metrics.hasHistoricalSystemRefund) {
                putHit(hits, "REFUND_MONTH_MISMATCH", "银联退款发生在核对期，但系统退款记录在核对区间外");
            } else {
                putHit(hits, "UNION_REFUND_ONLY", "银联核对期有退款流水，但系统核对期无退款成功记录");
            }
        } else if (systemRefund && metrics.systemCurrentRefundAmount != metrics.unionCurrentRefundAmount) {
            putHit(hits, "REFUND_AMOUNT_MISMATCH", "系统与银联核对期退款金额不一致");
        }
        // 核对区间前支付或核销、核对期才退款，即使退款金额一致也属于跨期异常。
        if ((systemRefund || unionRefund) && metrics.hasHistoricalSystemPayOrVerification) {
            putHit(hits, "CROSS_MONTH_REFUND", "核对区间前支付或核销的订单在核对期发生退款");
        }
        // 核对期核销后在区间外退款会减少当前有效核销金额，也必须承担本期差额调整。
        boolean refundedAfterCurrentVerification = metrics.hasCurrentVerification
                && ledger.details.stream()
                .filter(detail -> OrderDetail.OrderDetailStatusEnum.REFUND.getValue().equals(detail.getOrderStatus()))
                .map(OrderDetail::getRefundTime)
                .filter(Objects::nonNull)
                .anyMatch(time -> !time.isBefore(period.endDateTimeExclusive));
        if (refundedAfterCurrentVerification) {
            putHit(hits, "REFUND_AFTER_CURRENT_VERIFICATION",
                    "订单在核对期完成核销，但在核对区间后发生退款，当前有效核销金额已减少");
        }
        return new ArrayList<>(hits.values());
    }

    private boolean hasInvalidUnionAmount(List<UnionPayData> rows, boolean refund) {
        for (UnionPayData row : rows) {
            if (row.getAmount() == null || row.getAmount().compareTo(BigDecimal.ZERO) == 0) {
                return true;
            }
            if (!refund && row.getAmount().compareTo(BigDecimal.ZERO) < 0) {
                return true;
            }
        }
        return false;
    }

    private void putHit(Map<String, AnomalyHit> hits, String code, String explanation) {
        hits.putIfAbsent(code, new AnomalyHit(code, explanation));
    }

    private String chooseAdjustmentOwner(List<AnomalyHit> hits) {
        // 优先选择最能解释账面差额的业务异常，数据质量标签只作为关联标签展示。
        List<String> priorities = Arrays.asList(
                "CROSS_MONTH_APPOINTMENT", "HISTORICAL_PAY_CURRENT_VERIFICATION",
                "UNION_PAYMENT_ORDER_NOT_FOUND", "APPOINTMENT_NOT_VERIFIED",
                "UNION_PAYMENT_WITHOUT_CURRENT_VERIFICATION", "VERIFICATION_WITHOUT_ANY_PAYMENT",
                "SYSTEM_PAYMENT_UNION_MISSING", "PAY_AMOUNT_MISMATCH",
                "REFUND_AFTER_CURRENT_VERIFICATION", "CROSS_MONTH_REFUND",
                "UNION_REFUND_ONLY", "SYSTEM_REFUND_ONLY",
                "REFUND_AMOUNT_MISMATCH", "REFUND_MONTH_MISMATCH", "OVER_REFUND",
                "UNCLASSIFIED_BALANCE_DIFFERENCE");
        Set<String> codes = hits.stream().map(hit -> hit.code).collect(Collectors.toSet());
        for (String priority : priorities) {
            if (codes.contains(priority)) {
                return priority;
            }
        }
        return hits.get(0).code;
    }

    private void addHitsToCategories(Map<String, ReconciliationAbnormalResult.AbnormalCategory> categoryMap,
                                     Ledger ledger, LedgerMetrics metrics, List<AnomalyHit> hits,
                                     String adjustmentOwner, long adjustment) {
        // 一份完整异常详情会放入每个命中的分类，方便按分类直接查看。
        List<String> allCodes = hits.stream().map(hit -> hit.code).collect(Collectors.toList());
        for (AnomalyHit hit : hits) {
            ReconciliationAbnormalResult.AbnormalCategory category = categoryMap.get(hit.code);
            if (category == null) {
                continue;
            }
            ReconciliationAbnormalResult.AbnormalDetail detail = buildDetail(ledger, metrics);
            detail.setExplanation(hit.explanation);
            // 只有主分类记录调整额，其他分类调整额为0，从而避免总账重复调整。
            detail.setAdjustmentAmount(hit.code.equals(adjustmentOwner) ? adjustment : 0L);
            detail.setRelatedAnomalyCodes(allCodes.stream().filter(code -> !code.equals(hit.code))
                    .collect(Collectors.toList()));
            category.getDetails().add(detail);
            category.setAdjustmentAmount(category.getAdjustmentAmount() + detail.getAdjustmentAmount());
        }
    }

    private ReconciliationAbnormalResult.AbnormalDetail buildDetail(Ledger ledger, LedgerMetrics metrics) {
        // 将内部台账转换为前端可直接展示的系统、银联和退款比对快照。
        ReconciliationAbnormalResult.AbnormalDetail detail = new ReconciliationAbnormalResult.AbnormalDetail();
        detail.setTradeNo(ledger.tradeNo);
        ReconciliationAbnormalResult.SystemSnapshot system = detail.getSystem();
        Order order = metrics.primaryOrder;
        if (order != null) {
            system.setOrderExists(true);
            system.setOrderId(order.getId());
            system.setOrderNo(order.getOrderNo());
            system.setMuseumId(order.getMuseumId());
            system.setOrderType(order.getOrderType());
            system.setOrderQuantity(order.getOrderQuantity());
            system.setOrderStatus(order.getOrderStatus());
            system.setIsUsed(order.getIsUsed());
            system.setPayAmount(safeLong(order.getPayAmount()));
            system.setOrderRefundAmount(safeLong(order.getRefundAmount()));
            system.setRefundQuantity(order.getRefundQuantity());
            system.setTeamId(order.getTeamId());
            system.setBatchNo(order.getBatchNo());
            system.setVisitorId(order.getVisitorId());
            system.setCreateTime(order.getCreateTime());
            system.setUpdateTime(order.getUpdateTime());
            system.setPaySuccessTime(order.getPaySuccessTime());
            system.setAppointmentDate(order.getAppointmentDate());
            system.setVerificationTime(order.getVerificationTime());
        }
        system.setValidVerificationQuantity(metrics.systemValidQuantity);
        system.setValidVerificationAmount(metrics.systemValidAmount);
        system.setPeriodRefundCount(metrics.currentSystemRefundDetails.size());
        system.setPeriodRefundAmount(metrics.systemCurrentRefundAmount);
        system.setCumulativeRefundAmount(metrics.systemCumulativeRefundAmount);
        // 系统多笔退款逐条返回，不只返回汇总金额。
        for (OrderDetail refund : metrics.currentSystemRefundDetails) {
            ReconciliationAbnormalResult.SystemRefundDetail item = new ReconciliationAbnormalResult.SystemRefundDetail();
            item.setOrderDetailId(refund.getId());
            item.setRefundId(refund.getRefundId());
            item.setRefundTime(refund.getRefundTime());
            item.setRefundAmount(safeLong(refund.getRefundAmount()));
            system.getRefundDetails().add(item);
        }
        // 返回全部子订单及当前活动名称、单价，使前端不再调用旧detail接口补查。
        for (OrderDetail orderDetail : ledger.details) {
            ReconciliationAbnormalResult.SystemOrderDetail item =
                    new ReconciliationAbnormalResult.SystemOrderDetail();
            item.setOrderDetailId(orderDetail.getId());
            item.setOrderId(orderDetail.getOrderId());
            item.setOrderNo(orderDetail.getOrderNo());
            item.setMuseumId(orderDetail.getMuseumId());
            item.setActivityId(orderDetail.getActivityId());
            ActivityManage activity = ledger.activities.get(orderDetail.getActivityId());
            if (activity != null) {
                item.setActivityName(activity.getActivityName());
                item.setActivityPrice(safeLong(activity.getPrice()));
            }
            item.setActivityScheduleId(orderDetail.getActivityScheduleId());
            item.setOrderAmount(safeLong(orderDetail.getOrderAmount()));
            item.setRefundAmount(safeLong(orderDetail.getRefundAmount()));
            item.setOrderStatus(orderDetail.getOrderStatus());
            item.setRefundId(orderDetail.getRefundId());
            item.setRefundReason(orderDetail.getRefundReason());
            item.setRefundTime(orderDetail.getRefundTime());
            item.setCreateTime(orderDetail.getCreateTime());
            item.setUpdateTime(orderDetail.getUpdateTime());
            system.getOrderDetails().add(item);
        }

        ReconciliationAbnormalResult.UnionSnapshot union = detail.getUnion();
        union.setPaymentExists(!metrics.currentPayments.isEmpty());
        union.setPayCount(metrics.currentPayments.size());
        union.setPayAmount(metrics.unionCurrentPayAmount);
        for (UnionPayData payment : metrics.currentPayments) {
            union.getPaymentDetails().add(toPaymentDetail(payment));
        }
        union.setHistoricalPayCount(metrics.historicalPayments.size());
        union.setHistoricalPayAmount(metrics.unionHistoricalPayAmount);
        for (UnionPayData payment : metrics.historicalPayments) {
            union.getHistoricalPaymentDetails().add(toPaymentDetail(payment));
        }
        union.setRefundCount(metrics.currentRefunds.size());
        union.setRefundAmount(metrics.unionCurrentRefundAmount);
        // 银联多笔退款同样逐条返回，用参考号区分每一笔流水。
        for (UnionPayData refund : metrics.currentRefunds) {
            union.getRefundDetails().add(toRefundDetail(refund));
        }
        union.setHistoricalRefundCount(metrics.historicalRefunds.size());
        union.setHistoricalRefundAmount(metrics.unionHistoricalRefundAmount);
        for (UnionPayData refund : metrics.historicalRefunds) {
            union.getHistoricalRefundDetails().add(toRefundDetail(refund));
        }

        ReconciliationAbnormalResult.RefundCheck refundCheck = detail.getRefundCheck();
        refundCheck.setSystemRefundAmount(metrics.systemCurrentRefundAmount);
        refundCheck.setUnionRefundAmount(metrics.unionCurrentRefundAmount);
        // 退款差额方向固定为：系统退款金额 - 银联退款金额。
        refundCheck.setDifferenceAmount(metrics.systemCurrentRefundAmount - metrics.unionCurrentRefundAmount);
        refundCheck.setMatched(metrics.systemCurrentRefundAmount == metrics.unionCurrentRefundAmount);
        return detail;
    }

    private ReconciliationAbnormalResult.UnionPaymentDetail toPaymentDetail(UnionPayData payment) {
        ReconciliationAbnormalResult.UnionPaymentDetail item = new ReconciliationAbnormalResult.UnionPaymentDetail();
        item.setId(payment.getId());
        item.setMerchantName(payment.getMerchantName());
        item.setMid(payment.getMid());
        item.setTid(payment.getTid());
        item.setSettlementDate(payment.getSettlementDate());
        item.setTransactionTime(payment.getTransactionTime());
        item.setCardNo(payment.getCardNo());
        item.setAmount(toCents(payment.getAmount()));
        item.setHandlingCharge(toSignedCents(payment.getHandlingCharge()));
        item.setNetAmount(toSignedCents(payment.getNetAmount()));
        item.setReferenceNumber(payment.getReferenceNumber());
        item.setType(payment.getType());
        item.setChannel(payment.getChannel());
        item.setMerchantOrderNo(payment.getMerchantOrderNo());
        item.setUnionOrderNo(payment.getOrderNo());
        return item;
    }

    private ReconciliationAbnormalResult.UnionRefundDetail toRefundDetail(UnionPayData refund) {
        ReconciliationAbnormalResult.UnionRefundDetail item =
                new ReconciliationAbnormalResult.UnionRefundDetail();
        item.setId(refund.getId());
        item.setMerchantName(refund.getMerchantName());
        item.setMid(refund.getMid());
        item.setTid(refund.getTid());
        item.setSettlementDate(refund.getSettlementDate());
        item.setTransactionTime(refund.getTransactionTime());
        item.setCardNo(refund.getCardNo());
        item.setRefundAmount(toCents(refund.getAmount()));
        item.setHandlingCharge(toSignedCents(refund.getHandlingCharge()));
        item.setNetAmount(toSignedCents(refund.getNetAmount()));
        item.setReferenceNumber(refund.getReferenceNumber());
        item.setType(refund.getType());
        item.setChannel(refund.getChannel());
        item.setMerchantOrderNo(refund.getMerchantOrderNo());
        item.setUnionOrderNo(refund.getOrderNo());
        return item;
    }

    private ReconciliationAbnormalResult initializeResult(ReconciliationAbnormalQueryVO vo) {
        ReconciliationAbnormalResult result = new ReconciliationAbnormalResult();
        result.setMuseumId(vo.getMuseumId());
        result.setStartDate(vo.getStartDate());
        result.setEndDate(vo.getEndDate());
        result.getGroups().add(group(GROUP_PAYMENT, "支付与核销异常",
                category("CROSS_MONTH_APPOINTMENT", "核对期支付、跨期预约"),
                category("APPOINTMENT_NOT_VERIFIED", "预约到期未核销"),
                category("UNION_PAYMENT_WITHOUT_CURRENT_VERIFICATION", "银联有支付、系统核对期无核销"),
                category("UNION_PAYMENT_ORDER_NOT_FOUND", "银联有支付、系统订单不存在"),
                category("HISTORICAL_PAY_CURRENT_VERIFICATION", "区间前支付、核对期核销"),
                category("VERIFICATION_WITHOUT_ANY_PAYMENT", "核销订单无任何银联支付流水"),
                category("SYSTEM_PAYMENT_UNION_MISSING", "系统支付成功、银联核对期无流水"),
                category("PAY_AMOUNT_MISMATCH", "支付金额不一致"),
                category("DUPLICATE_UNION_PAYMENT", "银联重复支付流水")));
        result.getGroups().add(group(GROUP_REFUND, "退款异常",
                category("CROSS_MONTH_REFUND", "区间前支付或核销、核对期退款"),
                category("REFUND_AFTER_CURRENT_VERIFICATION", "核对期核销、区间后退款"),
                category("SYSTEM_REFUND_ONLY", "系统有退款、银联无退款"),
                category("UNION_REFUND_ONLY", "银联有退款、系统无退款"),
                category("REFUND_AMOUNT_MISMATCH", "退款金额不一致"),
                category("REFUND_MONTH_MISMATCH", "系统与银联退款日期区间不一致"),
                category("OVER_REFUND", "累计退款超过支付金额")));
        result.getGroups().add(group(GROUP_DATA, "订单数据异常",
                category("SYSTEM_TRADE_NO_MISSING", "系统订单缺少银联订单号"),
                category("DUPLICATE_SYSTEM_TRADE_NO", "系统银联订单号重复"),
                category("ORDER_REFUND_SUM_MISMATCH", "主子订单退款汇总不一致"),
                category("INVALID_VERIFICATION_STATUS", "核销状态与核销时间不一致"),
                category("INVALID_REFUND_STATUS", "退款状态与退款时间不一致"),
                category("UNCLASSIFIED_BALANCE_DIFFERENCE", "未归类余额差异")));
        result.getGroups().add(group(GROUP_IMPORT, "银联账单数据异常",
                category("UNION_TRADE_NO_MISSING", "银联流水缺少商户订单号"),
                category("UNION_AMOUNT_INVALID", "银联流水金额无效"),
                category("UNSUPPORTED_UNION_TYPE", "银联交易类型未识别"),
                category("DUPLICATE_UNION_REFERENCE", "银联参考号重复")));
        return result;
    }

    private ReconciliationAbnormalResult.AbnormalGroup group(String code, String name,
                                                              ReconciliationAbnormalResult.AbnormalCategory... categories) {
        ReconciliationAbnormalResult.AbnormalGroup group = new ReconciliationAbnormalResult.AbnormalGroup();
        group.setGroupCode(code);
        group.setGroupName(name);
        group.setCategories(new ArrayList<>(Arrays.asList(categories)));
        return group;
    }

    private ReconciliationAbnormalResult.AbnormalCategory category(String code, String name) {
        ReconciliationAbnormalResult.AbnormalCategory category = new ReconciliationAbnormalResult.AbnormalCategory();
        category.setAnomalyCode(code);
        category.setAnomalyName(name);
        return category;
    }

    private Map<String, ReconciliationAbnormalResult.AbnormalCategory> indexCategories(
            ReconciliationAbnormalResult result) {
        Map<String, ReconciliationAbnormalResult.AbnormalCategory> resultMap = new HashMap<>();
        for (ReconciliationAbnormalResult.AbnormalGroup group : result.getGroups()) {
            for (ReconciliationAbnormalResult.AbnormalCategory category : group.getCategories()) {
                resultMap.put(category.getAnomalyCode(), category);
            }
        }
        return resultMap;
    }

    private void finishCategories(ReconciliationAbnormalResult result) {
        for (ReconciliationAbnormalResult.AbnormalGroup group : result.getGroups()) {
            for (ReconciliationAbnormalResult.AbnormalCategory category : group.getCategories()) {
                category.setOrderCount(category.getDetails().size());
            }
        }
    }

    private void accumulateBalance(ReconciliationAbnormalResult.Balance balance, LedgerMetrics metrics) {
        balance.setSystemVerificationQuantity(balance.getSystemVerificationQuantity()
                + metrics.systemValidQuantity);
        balance.setSystemVerificationAmount(balance.getSystemVerificationAmount() + metrics.systemValidAmount);
        balance.setSystemRefundCount(balance.getSystemRefundCount() + metrics.currentSystemRefundDetails.size());
        balance.setSystemRefundAmount(balance.getSystemRefundAmount() + metrics.systemCurrentRefundAmount);
        balance.setUnionPayCount(balance.getUnionPayCount() + metrics.currentPayments.size());
        balance.setUnionPayAmount(balance.getUnionPayAmount() + metrics.unionCurrentPayAmount);
        balance.setUnionRefundCount(balance.getUnionRefundCount() + metrics.currentRefunds.size());
        balance.setUnionRefundAmount(balance.getUnionRefundAmount() + metrics.unionCurrentRefundAmount);
    }

    private void accumulateBilling(Map<String, ReconciliationAbnormalResult.BillingDetail> billingMap,
                                   Ledger ledger, QueryPeriod period) {
        for (Order order : ledger.orders) {
            if (!Integer.valueOf(1).equals(order.getIsUsed())
                    || !inPeriod(order.getVerificationTime(), period)) {
                continue;
            }
            String verificationMonth = YearMonth.from(order.getVerificationTime()).toString();
            for (OrderDetail detail : ledger.details) {
                if (!Objects.equals(order.getId(), detail.getOrderId())
                        || !OrderDetail.OrderDetailStatusEnum.PAY_SUCCESS.getValue()
                        .equals(detail.getOrderStatus())) {
                    continue;
                }
                ActivityManage activity = ledger.activities.get(detail.getActivityId());
                long activityPrice = activity == null ? 0L : safeLong(activity.getPrice());
                String key = verificationMonth + "|" + detail.getActivityId() + "|" + activityPrice;
                ReconciliationAbnormalResult.BillingDetail item = billingMap.computeIfAbsent(key, ignored -> {
                    ReconciliationAbnormalResult.BillingDetail value =
                            new ReconciliationAbnormalResult.BillingDetail();
                    value.setVerificationMonth(verificationMonth);
                    value.setActivityId(detail.getActivityId());
                    value.setActivityName(activity == null
                            ? (detail.getActivityId() == null ? "未知活动" : "活动ID " + detail.getActivityId())
                            : activity.getActivityName());
                    value.setActivityPrice(activityPrice);
                    return value;
                });
                item.setQuantity(item.getQuantity() + 1L);
                item.setAmount(item.getAmount() + safeLong(detail.getOrderAmount()));
            }
        }
    }

    private void finishBilling(ReconciliationAbnormalResult result,
                               Map<String, ReconciliationAbnormalResult.BillingDetail> billingMap) {
        List<ReconciliationAbnormalResult.BillingDetail> details = new ArrayList<>(billingMap.values());
        details.sort(Comparator.comparing(ReconciliationAbnormalResult.BillingDetail::getVerificationMonth)
                .thenComparing(ReconciliationAbnormalResult.BillingDetail::getActivityName,
                        Comparator.nullsLast(String::compareTo))
                .thenComparingLong(ReconciliationAbnormalResult.BillingDetail::getActivityPrice));
        result.getBilling().setDetails(details);
        result.getBilling().setTotalQuantity(details.stream()
                .mapToLong(ReconciliationAbnormalResult.BillingDetail::getQuantity).sum());
        result.getBilling().setTotalAmount(details.stream()
                .mapToLong(ReconciliationAbnormalResult.BillingDetail::getAmount).sum());
    }

    private void finishSourceControl(ReconciliationAbnormalResult result,
                                     List<OrderDetail> currentRefundDetails,
                                     List<UnionPayData> currentUnionRows,
                                     long unclassifiedCount) {
        // 记录四类原始数据的读取量和处理量，确保没有数据被静默遗漏。
        ReconciliationAbnormalResult.SourceControl source = result.getSourceControl();
        source.setSystemVerificationDetailCount(result.getBalance().getSystemVerificationQuantity());
        source.setClassifiedSystemVerificationDetailCount(source.getSystemVerificationDetailCount());
        source.setSystemRefundDetailCount(currentRefundDetails.size());
        source.setClassifiedSystemRefundDetailCount(currentRefundDetails.size());
        long payRows = currentUnionRows.stream().filter(row -> TYPE_PAY.equals(row.getType())).count();
        long refundRows = currentUnionRows.stream().filter(row -> TYPE_REFUND.equals(row.getType())).count();
        source.setUnionPayRowCount(payRows);
        source.setClassifiedUnionPayRowCount(payRows);
        source.setUnionRefundRowCount(refundRows);
        source.setClassifiedUnionRefundRowCount(refundRows);
        source.setUnclassifiedCount(unclassifiedCount);
        source.setDuplicateClassificationCount(0L);
    }

    private void finishBalance(ReconciliationAbnormalResult result) {
        ReconciliationAbnormalResult.Balance balance = result.getBalance();
        // 分类调整额合计只包含每个订单主异常承担的调整额。
        long adjustment = result.getGroups().stream().flatMap(group -> group.getCategories().stream())
                .mapToLong(ReconciliationAbnormalResult.AbnormalCategory::getAdjustmentAmount).sum();
        // 银联净额 = 核对期消费 - 核对期退款。
        balance.setUnionNetAmount(balance.getUnionPayAmount() - balance.getUnionRefundAmount());
        balance.setAbnormalAdjustmentAmount(adjustment);
        // 调整后银联净额 = 银联净额 + 异常调整额。
        balance.setAdjustedUnionNetAmount(balance.getUnionNetAmount() + adjustment);
        // 最终差额 = 系统有效核销金额 - 调整后银联净额；正常必须为0。
        balance.setBalanceDifference(balance.getSystemVerificationAmount() - balance.getAdjustedUnionNetAmount());
        ReconciliationAbnormalResult.SourceControl source = result.getSourceControl();
        // 金额闭环、无未知异常、无重复处理、四类数据数量一致时才算完全平衡。
        balance.setBalanced(balance.getBalanceDifference() == 0L
                && source.getUnclassifiedCount() == 0L
                && source.getDuplicateClassificationCount() == 0L
                && source.getSystemVerificationDetailCount() == source.getClassifiedSystemVerificationDetailCount()
                && source.getSystemRefundDetailCount() == source.getClassifiedSystemRefundDetailCount()
                && source.getUnionPayRowCount() == source.getClassifiedUnionPayRowCount()
                && source.getUnionRefundRowCount() == source.getClassifiedUnionRefundRowCount());
    }

    private long sumUnionAmount(List<UnionPayData> rows) {
        return rows.stream().mapToLong(row -> toCents(row.getAmount())).sum();
    }

    private long toCents(BigDecimal amount) {
        if (amount == null) {
            return 0L;
        }
        // 银联退款金额可能使用负数表示；业务返回统一取绝对值并转换成“分”。
        return amount.abs().movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    private long toSignedCents(BigDecimal amount) {
        if (amount == null) {
            return 0L;
        }
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    private static long safeLong(Integer value) {
        return value == null ? 0L : value.longValue();
    }

    private boolean inPeriod(LocalDateTime value, QueryPeriod period) {
        // 时间范围统一采用左闭右开，月末数据不会与下个月重复。
        return value != null && !value.isBefore(period.startDateTime)
                && value.isBefore(period.endDateTimeExclusive);
    }

    private boolean before(LocalDateTime value, LocalDateTime boundary) {
        return value != null && value.isBefore(boundary);
    }

    private static class QueryPeriod {
        private final LocalDate startDate;
        private final LocalDate endDateExclusive;
        private final LocalDateTime startDateTime;
        private final LocalDateTime endDateTimeExclusive;

        private QueryPeriod(LocalDate startDate, LocalDate endDateExclusive,
                            LocalDateTime startDateTime, LocalDateTime endDateTimeExclusive) {
            this.startDate = startDate;
            this.endDateExclusive = endDateExclusive;
            this.startDateTime = startDateTime;
            this.endDateTimeExclusive = endDateTimeExclusive;
        }
    }

    private static class Ledger {
        // 系统unionpay_order_no与银联merchant_order_no对应后的订单级交易号。
        private final String tradeNo;
        // 正常只有一个；保留列表是为了识别系统交易号重复。
        private final List<Order> orders = new ArrayList<>();
        // 系统订单的全部子订单，包含未退款、退款中和退款成功状态。
        private final List<OrderDetail> details = new ArrayList<>();
        // 银联核对期流水，只参与核对区间内账面净额计算。
        private final List<UnionPayData> currentUnionRows = new ArrayList<>();
        // 该交易号全部历史流水，用于判断跨月支付和退款。
        private final List<UnionPayData> allUnionRows = new ArrayList<>();
        // 子订单关联的活动快照，用于返回活动名称和固定单价。
        private final Map<Long, ActivityManage> activities = new HashMap<>();

        private Ledger(String tradeNo) {
            this.tradeNo = tradeNo;
        }
    }

    private static class LedgerMetrics {
        private Order primaryOrder;
        private long systemValidQuantity;
        private long systemValidAmount;
        private long systemCurrentRefundAmount;
        private long systemCumulativeRefundAmount;
        private long unionCurrentPayAmount;
        private long unionCurrentRefundAmount;
        private long unionHistoricalPayAmount;
        private long unionHistoricalRefundAmount;
        private long unionCumulativeRefundAmount;
        private long unionNetAmount;
        private boolean hasCurrentVerification;
        private boolean hasCurrentSystemPay;
        private boolean hasHistoricalSystemPayOrVerification;
        private boolean hasHistoricalSystemRefund;
        private List<OrderDetail> currentSystemRefundDetails = new ArrayList<>();
        private List<UnionPayData> currentPayments = new ArrayList<>();
        private List<UnionPayData> currentRefunds = new ArrayList<>();
        private List<UnionPayData> currentUnsupportedRows = new ArrayList<>();
        private List<UnionPayData> historicalPayments = new ArrayList<>();
        private List<UnionPayData> historicalRefunds = new ArrayList<>();
        private List<UnionPayData> allPayments = new ArrayList<>();
        private List<UnionPayData> allRefunds = new ArrayList<>();
    }

    private static class AnomalyHit {
        private final String code;
        private final String explanation;

        private AnomalyHit(String code, String explanation) {
            this.code = code;
            this.explanation = explanation;
        }
    }
}
