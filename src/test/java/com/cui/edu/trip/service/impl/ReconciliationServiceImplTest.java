package com.cui.edu.trip.service.impl;

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
import com.cui.edu.vo.reconciliation.ReconciliationAbnormalQueryVO;
import com.cui.edu.vo.reconciliation.ReconciliationAbnormalResult;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReconciliationServiceImplTest {

    @Test
    void supportsInclusivePartialDateRangeAcrossMonths() {
        Fixture fixture = fixture();
        ReconciliationAbnormalQueryVO vo = query(
                LocalDate.of(2026, 1, 18), LocalDate.of(2026, 3, 2));
        Order order = order(6L, "UP006", 8000,
                LocalDateTime.of(2026, 1, 18, 10, 0),
                LocalDate.of(2026, 3, 2),
                LocalDateTime.of(2026, 3, 2, 23, 59, 59), 1);
        OrderDetail detail = detail(61L, 6L, 8000,
                OrderDetail.OrderDetailStatusEnum.PAY_SUCCESS.getValue(), null, null);
        detail.setActivityId(6L);
        ActivityManage activity = new ActivityManage();
        activity.setId(6L);
        activity.setActivityName("跨月测试活动");
        activity.setPrice(8000);
        UnionPayData payment = unionRow(601L, "UP006", "消费", "80.00",
                LocalDateTime.of(2026, 1, 18, 10, 1));

        when(fixture.activityManageMapper.selectList(any())).thenReturn(Collections.singletonList(activity));
        fixture.stub(Collections.emptyList(), Collections.singletonList(detail),
                Collections.singletonList(order), Collections.singletonList(payment),
                Collections.singletonList(payment));

        ReconciliationAbnormalResult result = fixture.service.findAbnormalData(vo);

        assertEquals(LocalDate.of(2026, 1, 18), result.getStartDate());
        assertEquals(LocalDate.of(2026, 3, 2), result.getEndDate());
        assertEquals(8000L, result.getBalance().getSystemVerificationAmount());
        assertEquals(8000L, result.getBalance().getUnionNetAmount());
        assertEquals(1L, result.getBilling().getTotalQuantity());
        assertEquals(8000L, result.getBilling().getTotalAmount());
        assertEquals(1, result.getBilling().getDetails().size());
        assertEquals("2026-03", result.getBilling().getDetails().get(0).getVerificationMonth());
        assertEquals("跨月测试活动", result.getBilling().getDetails().get(0).getActivityName());
        assertTrue(result.getBalance().isBalanced());
    }

    @Test
    void rejectsEndDateBeforeStartDate() {
        Fixture fixture = fixture();
        ReconciliationAbnormalQueryVO vo = query(
                LocalDate.of(2026, 1, 18), LocalDate.of(2026, 1, 1));

        assertThrows(RuntimeException.class, () -> fixture.service.findAbnormalData(vo));
    }

    @Test
    void classifiesCurrentPayWithFutureAppointment() {
        Fixture fixture = fixture();
        Order order = order(1L, "UP001", 20000,
                LocalDateTime.of(2026, 7, 16, 10, 0),
                LocalDate.of(2026, 10, 8), null, 0);
        OrderDetail detail = detail(11L, 1L, 20000,
                OrderDetail.OrderDetailStatusEnum.PAY_SUCCESS.getValue(), null, null);
        detail.setOrderNo("DETAIL001");
        detail.setActivityId(7L);
        ActivityManage activity = new ActivityManage();
        activity.setId(7L);
        activity.setActivityName("测试活动");
        activity.setPrice(20000);
        UnionPayData payment = unionRow(101L, "UP001", "消费", "200.00",
                LocalDateTime.of(2026, 7, 16, 10, 1));

        when(fixture.activityManageMapper.selectList(any())).thenReturn(Collections.singletonList(activity));
        fixture.stub(Collections.emptyList(), Collections.singletonList(detail),
                Collections.singletonList(order), Collections.singletonList(payment),
                Collections.singletonList(payment));

        ReconciliationAbnormalResult result = fixture.service.findAbnormalData(query());
        ReconciliationAbnormalResult.AbnormalCategory category = category(result, "CROSS_MONTH_APPOINTMENT");

        assertEquals(1, category.getOrderCount());
        assertEquals(-20000L, category.getAdjustmentAmount());
        assertEquals("UP001", category.getDetails().get(0).getTradeNo());
        ReconciliationAbnormalResult.SystemSnapshot system = category.getDetails().get(0).getSystem();
        assertEquals(1, system.getOrderDetails().size());
        assertEquals("DETAIL001", system.getOrderDetails().get(0).getOrderNo());
        assertEquals("测试活动", system.getOrderDetails().get(0).getActivityName());
        assertEquals(20000L, system.getOrderDetails().get(0).getActivityPrice());
        assertEquals(0L, result.getBalance().getSystemVerificationAmount());
        assertEquals(20000L, result.getBalance().getUnionPayAmount());
        assertTrue(result.getBalance().isBalanced());
    }

    @Test
    void classifiesHistoricalPayVerifiedInCurrentMonth() {
        Fixture fixture = fixture();
        Order order = order(2L, "UP002", 10000,
                LocalDateTime.of(2026, 6, 10, 10, 0),
                LocalDate.of(2026, 7, 8),
                LocalDateTime.of(2026, 7, 8, 11, 0), 1);
        OrderDetail detail = detail(21L, 2L, 10000,
                OrderDetail.OrderDetailStatusEnum.PAY_SUCCESS.getValue(), null, null);
        UnionPayData historicalPayment = unionRow(201L, "UP002", "消费", "100.00",
                LocalDateTime.of(2026, 6, 10, 10, 1));

        fixture.stub(Collections.emptyList(), Collections.singletonList(detail),
                Collections.singletonList(order), Collections.emptyList(),
                Collections.singletonList(historicalPayment));

        ReconciliationAbnormalResult result = fixture.service.findAbnormalData(query());
        ReconciliationAbnormalResult.AbnormalCategory category = category(result,
                "HISTORICAL_PAY_CURRENT_VERIFICATION");

        assertEquals(1, category.getOrderCount());
        assertEquals(10000L, category.getAdjustmentAmount());
        assertEquals(10000L, result.getBalance().getSystemVerificationAmount());
        assertEquals(0L, result.getBalance().getUnionPayAmount());
        assertTrue(result.getBalance().isBalanced());
    }

    @Test
    void classifiesMatchedRefundForHistoricalOrderAsCrossMonthRefund() {
        Fixture fixture = fixture();
        Order order = order(3L, "UP003", 10000,
                LocalDateTime.of(2026, 6, 1, 10, 0),
                LocalDate.of(2026, 6, 1),
                LocalDateTime.of(2026, 6, 1, 11, 0), 1);
        order.setOrderStatus(Order.OrderStatusEnum.PARTIAL_REFUND.getValue());
        order.setRefundAmount(3000);
        OrderDetail refundDetail1 = detail(31L, 3L, 1000,
                OrderDetail.OrderDetailStatusEnum.REFUND.getValue(),
                LocalDateTime.of(2026, 7, 5, 10, 0), "R003");
        OrderDetail refundDetail2 = detail(32L, 3L, 2000,
                OrderDetail.OrderDetailStatusEnum.REFUND.getValue(),
                LocalDateTime.of(2026, 7, 10, 10, 0), "R004");
        UnionPayData historicalPayment = unionRow(301L, "UP003", "消费", "100.00",
                LocalDateTime.of(2026, 6, 1, 10, 1));
        UnionPayData refund1 = unionRow(302L, "UP003", "联机退货", "-10.00",
                LocalDateTime.of(2026, 7, 5, 10, 1));
        UnionPayData refund2 = unionRow(303L, "UP003", "联机退货", "-20.00",
                LocalDateTime.of(2026, 7, 10, 10, 1));

        fixture.stub(Arrays.asList(refundDetail1, refundDetail2), Arrays.asList(refundDetail1, refundDetail2),
                Collections.singletonList(order), Arrays.asList(refund1, refund2),
                Arrays.asList(historicalPayment, refund1, refund2));

        ReconciliationAbnormalResult result = fixture.service.findAbnormalData(query());
        ReconciliationAbnormalResult.AbnormalCategory category = category(result, "CROSS_MONTH_REFUND");

        assertEquals(1, category.getOrderCount());
        assertEquals(3000L, category.getAdjustmentAmount());
        assertEquals(3000L, result.getBalance().getSystemRefundAmount());
        assertEquals(3000L, result.getBalance().getUnionRefundAmount());
        assertEquals(2L, result.getBalance().getSystemRefundCount());
        assertEquals(2L, result.getBalance().getUnionRefundCount());
        assertTrue(category.getDetails().get(0).getRefundCheck().isMatched());
        assertTrue(result.getBalance().isBalanced());
    }

    @Test
    void treatsMatchedSameMonthPartialRefundAsNormal() {
        Fixture fixture = fixture();
        Order order = order(5L, "UP005", 10000,
                LocalDateTime.of(2026, 7, 3, 10, 0),
                LocalDate.of(2026, 7, 3),
                LocalDateTime.of(2026, 7, 3, 11, 0), 1);
        order.setOrderStatus(Order.OrderStatusEnum.PARTIAL_REFUND.getValue());
        order.setRefundAmount(3000);
        OrderDetail validDetail = detail(51L, 5L, 7000,
                OrderDetail.OrderDetailStatusEnum.PAY_SUCCESS.getValue(), null, null);
        OrderDetail refundDetail = detail(52L, 5L, 3000,
                OrderDetail.OrderDetailStatusEnum.REFUND.getValue(),
                LocalDateTime.of(2026, 7, 4, 10, 0), "R005");
        UnionPayData payment = unionRow(501L, "UP005", "消费", "100.00",
                LocalDateTime.of(2026, 7, 3, 10, 1));
        UnionPayData refund = unionRow(502L, "UP005", "联机退货", "-30.00",
                LocalDateTime.of(2026, 7, 4, 10, 1));

        fixture.stub(Collections.singletonList(refundDetail), Arrays.asList(validDetail, refundDetail),
                Collections.singletonList(order), Arrays.asList(payment, refund),
                Arrays.asList(payment, refund));

        ReconciliationAbnormalResult result = fixture.service.findAbnormalData(query());
        long abnormalDetailCount = result.getGroups().stream().flatMap(group -> group.getCategories().stream())
                .mapToLong(category -> category.getDetails().size()).sum();

        assertEquals(0L, abnormalDetailCount);
        assertEquals(7000L, result.getBalance().getSystemVerificationAmount());
        assertEquals(7000L, result.getBalance().getUnionNetAmount());
        assertTrue(result.getBalance().isBalanced());
    }

    @Test
    void classifiesRefundAfterCurrentMonthVerification() {
        Fixture fixture = fixture();
        Order order = order(4L, "UP004", 10000,
                LocalDateTime.of(2026, 7, 2, 10, 0),
                LocalDate.of(2026, 7, 2),
                LocalDateTime.of(2026, 7, 2, 11, 0), 1);
        order.setOrderStatus(Order.OrderStatusEnum.PARTIAL_REFUND.getValue());
        order.setRefundAmount(3000);
        OrderDetail validDetail = detail(41L, 4L, 7000,
                OrderDetail.OrderDetailStatusEnum.PAY_SUCCESS.getValue(), null, null);
        OrderDetail futureRefund = detail(42L, 4L, 3000,
                OrderDetail.OrderDetailStatusEnum.REFUND.getValue(),
                LocalDateTime.of(2026, 8, 5, 10, 0), "R004");
        UnionPayData payment = unionRow(401L, "UP004", "消费", "100.00",
                LocalDateTime.of(2026, 7, 2, 10, 1));
        UnionPayData refund = unionRow(402L, "UP004", "联机退货", "-30.00",
                LocalDateTime.of(2026, 8, 5, 10, 1));

        fixture.stub(Collections.emptyList(), Arrays.asList(validDetail, futureRefund),
                Collections.singletonList(order), Collections.singletonList(payment),
                Arrays.asList(payment, refund));

        ReconciliationAbnormalResult result = fixture.service.findAbnormalData(query());
        ReconciliationAbnormalResult.AbnormalCategory category = category(result,
                "REFUND_AFTER_CURRENT_VERIFICATION");

        assertEquals(1, category.getOrderCount());
        assertEquals(-3000L, category.getAdjustmentAmount());
        assertEquals(7000L, result.getBalance().getSystemVerificationAmount());
        assertEquals(10000L, result.getBalance().getUnionNetAmount());
        assertTrue(result.getBalance().isBalanced());
    }

    private ReconciliationAbnormalQueryVO query() {
        return query(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
    }

    private ReconciliationAbnormalQueryVO query(LocalDate startDate, LocalDate endDate) {
        ReconciliationAbnormalQueryVO vo = new ReconciliationAbnormalQueryVO();
        vo.setMuseumId("1");
        vo.setStartDate(startDate);
        vo.setEndDate(endDate);
        return vo;
    }

    private ReconciliationAbnormalResult.AbnormalCategory category(ReconciliationAbnormalResult result,
                                                                    String code) {
        return result.getGroups().stream().flatMap(group -> group.getCategories().stream())
                .filter(item -> code.equals(item.getAnomalyCode())).findFirst()
                .orElseThrow(AssertionError::new);
    }

    private Order order(Long id, String tradeNo, Integer payAmount, LocalDateTime payTime,
                        LocalDate appointmentDate, LocalDateTime verificationTime, Integer isUsed) {
        Order order = new Order();
        order.setId(id);
        order.setOrderNo("SYS" + id);
        order.setMuseumId(1L);
        order.setUnionpayOrderNo(tradeNo);
        order.setPayAmount(payAmount);
        order.setOrderType(1);
        order.setOrderQuantity(1);
        order.setPaySuccessTime(payTime);
        order.setAppointmentDate(appointmentDate);
        order.setVerificationTime(verificationTime);
        order.setIsUsed(isUsed);
        order.setOrderStatus(Order.OrderStatusEnum.SUCCESS.getValue());
        order.setRefundAmount(0);
        return order;
    }

    private OrderDetail detail(Long id, Long orderId, Integer amount, Integer status,
                               LocalDateTime refundTime, String refundId) {
        OrderDetail detail = new OrderDetail();
        detail.setId(id);
        detail.setOrderId(orderId);
        detail.setMuseumId(1L);
        detail.setOrderStatus(status);
        detail.setOrderAmount(amount);
        detail.setRefundAmount(status.equals(OrderDetail.OrderDetailStatusEnum.REFUND.getValue()) ? amount : 0);
        detail.setRefundTime(refundTime);
        detail.setRefundId(refundId);
        return detail;
    }

    private UnionPayData unionRow(Long id, String tradeNo, String type, String amount,
                                  LocalDateTime transactionTime) {
        UnionPayData row = new UnionPayData();
        row.setId(id);
        row.setMid("MID1");
        row.setTid("TID1");
        row.setMerchantOrderNo(tradeNo);
        row.setType(type);
        row.setAmount(new BigDecimal(amount));
        row.setTransactionTime(transactionTime);
        row.setReferenceNumber("REF" + id);
        return row;
    }

    private Fixture fixture() {
        Fixture fixture = new Fixture();
        fixture.service = new ReconciliationServiceImpl();
        fixture.orderMapper = mock(OrderMapper.class);
        fixture.orderDetailMapper = mock(OrderDetailMapper.class);
        fixture.unionPayDataMapper = mock(UnionPayDataMapper.class);
        fixture.activityManageMapper = mock(ActivityManageMapper.class);
        fixture.museumService = mock(MuseumService.class);
        Museum museum = new Museum();
        museum.setId(1L);
        museum.setMid("MID1");
        museum.setTid("TID1");
        when(fixture.museumService.getById("1")).thenReturn(museum);
        ReflectionTestUtils.setField(fixture.service, "orderMapper", fixture.orderMapper);
        ReflectionTestUtils.setField(fixture.service, "orderDetailMapper", fixture.orderDetailMapper);
        ReflectionTestUtils.setField(fixture.service, "unionPayDataMapper", fixture.unionPayDataMapper);
        ReflectionTestUtils.setField(fixture.service, "activityManageMapper", fixture.activityManageMapper);
        ReflectionTestUtils.setField(fixture.service, "museumService", fixture.museumService);
        return fixture;
    }

    private static class Fixture {
        private ReconciliationServiceImpl service;
        private OrderMapper orderMapper;
        private OrderDetailMapper orderDetailMapper;
        private UnionPayDataMapper unionPayDataMapper;
        private ActivityManageMapper activityManageMapper;
        private MuseumService museumService;

        private void stub(java.util.List<OrderDetail> currentRefundDetails,
                          java.util.List<OrderDetail> allDetails,
                          java.util.List<Order> orders,
                          java.util.List<UnionPayData> currentUnionRows,
                          java.util.List<UnionPayData> historicalUnionRows) {
            when(orderDetailMapper.selectList(any())).thenReturn(currentRefundDetails, allDetails);
            when(orderMapper.selectList(any())).thenReturn(orders);
            when(unionPayDataMapper.selectList(any())).thenReturn(currentUnionRows, historicalUnionRows);
        }
    }
}
