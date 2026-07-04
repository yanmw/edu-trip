package com.cui.edu.trip.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cui.edu.common.PageResult;
import com.cui.edu.common.SysConstants;
import com.cui.edu.config.redis.DistributedLockHandler;
import com.cui.edu.system.entity.Museum;
import com.cui.edu.system.service.MuseumService;
import com.cui.edu.trip.entity.Evaluation;
import com.cui.edu.trip.entity.Order;
import com.cui.edu.trip.entity.OrderDetail;
import com.cui.edu.trip.entity.OrderLog;
import com.cui.edu.trip.entity.Team;
import com.cui.edu.trip.entity.Visitor;
import com.cui.edu.trip.mapper.OrderMapper;
import com.cui.edu.trip.service.EvaluationService;
import com.cui.edu.trip.service.OrderDetailService;
import com.cui.edu.trip.service.OrderLogService;
import com.cui.edu.trip.service.TeamService;
import com.cui.edu.trip.service.VisitorService;
import com.cui.edu.util.TextCodeGenerator;
import com.cui.edu.vo.trip.AppointmentVO;
import com.cui.edu.vo.trip.OrderVO;
import com.cui.edu.vo.trip.VerificationVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceImplTest {

    static {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Order.class);
    }

    @Test
    void findAdminPageAllowsQueryingAllOrdersWithoutVisitorOrTeam() {
        OrderMapper orderMapper = mock(OrderMapper.class);
        when(orderMapper.selectPage(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        OrderServiceImpl orderService = orderService(orderMapper);

        OrderVO vo = new OrderVO();
        vo.setPageNum(1);
        vo.setPageSize(10);

        PageResult result = orderService.findAdminPage(vo);

        assertEquals(1, result.getPageNum());
        assertEquals(10, result.getPageSize());
        ArgumentCaptor<Wrapper<Order>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(orderMapper).selectPage(any(Page.class), queryCaptor.capture());
        String sqlSegment = queryCaptor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("is_deleted"));
        assertFalse(sqlSegment.contains("visitor_id"));
        assertFalse(sqlSegment.contains("team_id"));
    }

    @Test
    void findAdminPageAppliesManagerFilters() {
        OrderMapper orderMapper = mock(OrderMapper.class);
        when(orderMapper.selectPage(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        OrderServiceImpl orderService = orderService(orderMapper);

        OrderVO vo = new OrderVO();
        vo.setPageNum(1);
        vo.setPageSize(10);
        vo.setOrderNo("202606150001");
        vo.setMuseumId(3L);
        vo.setOrderStatus(Order.OrderStatusEnum.SUCCESS.getValue());
        vo.setOrderType(2);
        vo.setIsUsed(0);
        vo.setBatchNo("BATCH-20261001");
        vo.setAppointmentDate(LocalDate.of(2026, 10, 1));

        orderService.findAdminPage(vo);

        ArgumentCaptor<Wrapper<Order>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(orderMapper).selectPage(any(Page.class), queryCaptor.capture());
        String sqlSegment = queryCaptor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("order_no"));
        assertTrue(sqlSegment.contains("museum_id"));
        assertTrue(sqlSegment.contains("order_status"));
        assertTrue(sqlSegment.contains("order_type"));
        assertTrue(sqlSegment.contains("is_used"));
        assertTrue(sqlSegment.contains("batch_no"));
        assertTrue(sqlSegment.contains("appointment_date"));
    }

    @Test
    void findPageAppliesBatchNoForTeamOrders() {
        OrderMapper orderMapper = mock(OrderMapper.class);
        when(orderMapper.selectPage(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        OrderServiceImpl orderService = orderService(orderMapper);

        OrderVO vo = new OrderVO();
        vo.setPageNum(1);
        vo.setPageSize(10);
        vo.setTeamId(9L);
        vo.setBatchNo("BATCH-20261001");

        orderService.findPage(vo);

        ArgumentCaptor<Wrapper<Order>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(orderMapper).selectPage(any(Page.class), queryCaptor.capture());
        String sqlSegment = queryCaptor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("team_id"));
        assertTrue(sqlSegment.contains("batch_no"));
    }

    @Test
    void findPageMarksWhetherCurrentPageOrdersHaveEvaluation() {
        OrderMapper orderMapper = mock(OrderMapper.class);
        when(orderMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<Order> page = invocation.getArgument(0);
            Order evaluatedOrder = new Order();
            evaluatedOrder.setId(1L);
            evaluatedOrder.setOrderNo("202606170001");
            evaluatedOrder.setTeamId(9L);
            Order notEvaluatedOrder = new Order();
            notEvaluatedOrder.setId(2L);
            notEvaluatedOrder.setOrderNo("202606170002");
            notEvaluatedOrder.setTeamId(9L);
            page.setRecords(Arrays.asList(evaluatedOrder, notEvaluatedOrder));
            page.setTotal(2);
            return page;
        });
        OrderDetailService orderDetailService = mock(OrderDetailService.class);
        when(orderDetailService.list(any())).thenReturn(Collections.emptyList());
        TeamService teamService = mock(TeamService.class);
        when(teamService.listByIds(any())).thenReturn(Collections.emptyList());
        VisitorService visitorService = mock(VisitorService.class);
        when(visitorService.list(any(Wrapper.class))).thenReturn(Collections.emptyList());
        EvaluationService evaluationService = mock(EvaluationService.class);
        Evaluation evaluation = new Evaluation();
        evaluation.setOrderId(1L);
        when(evaluationService.list(any(Wrapper.class))).thenReturn(Collections.singletonList(evaluation));

        OrderServiceImpl orderService = orderService(orderMapper);
        ReflectionTestUtils.setField(orderService, "orderDetailService", orderDetailService);
        ReflectionTestUtils.setField(orderService, "teamService", teamService);
        ReflectionTestUtils.setField(orderService, "visitorService", visitorService);
        ReflectionTestUtils.setField(orderService, "evaluationService", evaluationService);

        OrderVO vo = new OrderVO();
        vo.setPageNum(1);
        vo.setPageSize(10);
        vo.setTeamId(9L);
        PageResult result = orderService.findPage(vo);

        List<Order> orders = (List<Order>) result.getContent();
        assertEquals(SysConstants.IS_TRUE, orders.get(0).getIsEvaluated());
        assertEquals(SysConstants.IS_FALSE, orders.get(1).getIsEvaluated());
    }

    @Test
    void findByOrderNoReturnsNotDeletedOrderWithDetailList() {
        OrderMapper orderMapper = mock(OrderMapper.class);
        Order order = new Order();
        order.setId(7L);
        order.setOrderNo("202606150001");
        when(orderMapper.selectOne(any())).thenReturn(order);

        OrderDetailService orderDetailService = mock(OrderDetailService.class);
        when(orderDetailService.list(any())).thenReturn(Collections.emptyList());
        OrderServiceImpl orderService = orderService(orderMapper);
        ReflectionTestUtils.setField(orderService, "orderDetailService", orderDetailService);

        Order result = orderService.findByOrderNo("202606150001");

        assertEquals(7L, result.getId());
        assertNotNull(result.getDetailList());
        assertTrue(result.getDetailList().isEmpty());
        ArgumentCaptor<Wrapper<Order>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(orderMapper).selectOne(queryCaptor.capture());
        String sqlSegment = queryCaptor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("order_no"));
        assertTrue(sqlSegment.contains("is_deleted"));
    }

    @Test
    void fillOrderRelationInfoReturnsPersonalVisitorAndTeamBatchVisitors() {
        Order personalOrder = new Order();
        personalOrder.setOrderNo("P202606160001");
        personalOrder.setVisitorId(8L);

        Order teamOrderBatchOne = new Order();
        teamOrderBatchOne.setOrderNo("T202606160001");
        teamOrderBatchOne.setTeamId(9L);
        teamOrderBatchOne.setBatchNo("BATCH-1");

        Order teamOrderBatchTwo = new Order();
        teamOrderBatchTwo.setOrderNo("T202606160002");
        teamOrderBatchTwo.setTeamId(9L);
        teamOrderBatchTwo.setBatchNo("BATCH-2");

        Visitor personalVisitor = new Visitor();
        personalVisitor.setId(8L);
        personalVisitor.setName("个人游客");

        Visitor teamVisitorOne = new Visitor();
        teamVisitorOne.setId(101L);
        teamVisitorOne.setTeamId(9L);
        teamVisitorOne.setBatchNo("BATCH-1");
        teamVisitorOne.setName("团队游客A");

        Visitor teamVisitorTwo = new Visitor();
        teamVisitorTwo.setId(102L);
        teamVisitorTwo.setTeamId(9L);
        teamVisitorTwo.setBatchNo("BATCH-2");
        teamVisitorTwo.setName("团队游客B");

        Team team = new Team();
        team.setId(9L);
        team.setTeamName("研学团队");

        VisitorService visitorService = mock(VisitorService.class);
        when(visitorService.listByIds(any())).thenReturn(Collections.singletonList(personalVisitor));
        when(visitorService.list(any(Wrapper.class))).thenReturn(Arrays.asList(teamVisitorOne, teamVisitorTwo));
        TeamService teamService = mock(TeamService.class);
        when(teamService.listByIds(any())).thenReturn(Collections.singletonList(team));

        OrderServiceImpl orderService = orderService(mock(OrderMapper.class));
        ReflectionTestUtils.setField(orderService, "visitorService", visitorService);
        ReflectionTestUtils.setField(orderService, "teamService", teamService);

        List<Order> orderList = Arrays.asList(personalOrder, teamOrderBatchOne, teamOrderBatchTwo);
        ReflectionTestUtils.invokeMethod(orderService, "fillOrderRelationInfo", orderList);

        assertEquals(personalVisitor, personalOrder.getVisitor());
        assertNotNull(teamOrderBatchOne.getTeam());
        assertNotNull(teamOrderBatchTwo.getTeam());
        assertNotSame(teamOrderBatchOne.getTeam(), teamOrderBatchTwo.getTeam());
        assertEquals(1, teamOrderBatchOne.getTeam().getVisitorList().size());
        assertEquals("团队游客A", teamOrderBatchOne.getTeam().getVisitorList().get(0).getName());
        assertEquals(1, teamOrderBatchTwo.getTeam().getVisitorList().size());
        assertEquals("团队游客B", teamOrderBatchTwo.getTeam().getVisitorList().get(0).getName());
    }

    @Test
    void addTeamOrderRequiresBatchNo() throws Exception {
        OrderServiceImpl orderService = orderServiceWithPassingSlotLock(mock(OrderMapper.class));
        AppointmentVO vo = baseAppointment();
        vo.setTeamId(9L);

        Map result = orderService.add(vo, null);

        assertEquals("团队下单时游客批次号不能为空", result.get("msg"));
    }

    @Test
    void addPersonalOrderRejectsBatchNo() throws Exception {
        OrderServiceImpl orderService = orderServiceWithPassingSlotLock(mock(OrderMapper.class));
        AppointmentVO vo = baseAppointment();
        vo.setVisitorId(8L);
        vo.setBatchNo("BATCH-20261001");

        Map result = orderService.add(vo, null);

        assertEquals("个人下单时不能传游客批次号", result.get("msg"));
    }

    @Test
    void buildPayingOrderCopiesBatchNoForTeamOrder() {
        OrderServiceImpl orderService = orderService(mock(OrderMapper.class));
        TextCodeGenerator textCodeGenerator = mock(TextCodeGenerator.class);
        when(textCodeGenerator.generate()).thenReturn("202606160001");
        ReflectionTestUtils.setField(orderService, "textCodeGenerator", textCodeGenerator);
        AppointmentVO vo = baseAppointment();
        vo.setTeamId(9L);
        vo.setBatchNo("BATCH-20261001");

        Order order = ReflectionTestUtils.invokeMethod(orderService, "buildPayingOrder", vo, 1);

        assertNotNull(order);
        assertEquals("BATCH-20261001", order.getBatchNo());
    }

    @Test
    void checkAppointmentTeamUsesBatchNoWhenCheckingPayingOrder() {
        OrderMapper orderMapper = mock(OrderMapper.class);
        when(orderMapper.selectCount(any())).thenReturn(0);
        OrderServiceImpl orderService = orderService(orderMapper);

        TeamService teamService = mock(TeamService.class);
        when(teamService.getById(9L)).thenReturn(new Team());
        ReflectionTestUtils.setField(orderService, "teamService", teamService);

        AppointmentVO vo = baseAppointment();
        vo.setTeamId(9L);
        vo.setBatchNo("BATCH-20261001");
        String checkMsg = ReflectionTestUtils.invokeMethod(orderService, "checkAppointmentTeam", vo);

        assertEquals(null, checkMsg);
        ArgumentCaptor<Wrapper<Order>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(orderMapper).selectCount(queryCaptor.capture());
        String sqlSegment = queryCaptor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("team_id"));
        assertTrue(sqlSegment.contains("batch_no"));
    }

    @Test
    void verificationRecordsVerificationTimeWhenOrderIsUsed() {
        OrderMapper orderMapper = mock(OrderMapper.class);
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        Order order = new Order();
        order.setId(7L);
        order.setOrderNo("202606150001");
        order.setMuseumId(3L);
        order.setOrderStatus(Order.OrderStatusEnum.SUCCESS.getValue());
        order.setIsUsed(SysConstants.IS_FALSE);
        order.setOrderQuantity(1);
        order.setAppointmentDate(LocalDate.now());
        when(orderMapper.selectOne(any())).thenReturn(order);

        Museum museum = new Museum();
        museum.setStatus(SysConstants.IS_TRUE);
        MuseumService museumService = mock(MuseumService.class);
        when(museumService.getById(3L)).thenReturn(museum);

        OrderDetail orderDetail = new OrderDetail();
        orderDetail.setOrderStatus(OrderDetail.OrderDetailStatusEnum.PAY_SUCCESS.getValue());
        OrderDetailService orderDetailService = mock(OrderDetailService.class);
        when(orderDetailService.list(any())).thenReturn(Collections.singletonList(orderDetail));

        OrderServiceImpl orderService = orderService(orderMapper);
        ReflectionTestUtils.setField(orderService, "museumService", museumService);
        ReflectionTestUtils.setField(orderService, "orderDetailService", orderDetailService);
        ReflectionTestUtils.setField(orderService, "orderLogService", mock(OrderLogService.class));

        VerificationVO vo = new VerificationVO();
        vo.setOrderNo("202606150001");
        vo.setMuseumId("3");
        Map result = orderService.verification(vo);

        assertFalse(result.containsKey("msg"));
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).updateById(orderCaptor.capture());
        Order updatedOrder = orderCaptor.getValue();
        assertEquals(SysConstants.IS_TRUE, updatedOrder.getIsUsed());
        assertNotNull(updatedOrder.getVerificationTime());
    }

    @Test
    void refundRecordsOperationRequestWhenSingleRefundCheckFails() throws Exception {
        OrderMapper orderMapper = mock(OrderMapper.class);
        OrderDetailService orderDetailService = mock(OrderDetailService.class);
        OrderLogService orderLogService = mock(OrderLogService.class);

        OrderDetail orderDetail = new OrderDetail();
        orderDetail.setId(11L);
        orderDetail.setOrderId(7L);
        orderDetail.setOrderNo("202606170001");
        orderDetail.setOrderStatus(OrderDetail.OrderDetailStatusEnum.REFUNDING.getValue());
        when(orderDetailService.getById(11L)).thenReturn(orderDetail);

        Order order = new Order();
        order.setId(7L);
        order.setOrderNo("202606170001");
        order.setOrderStatus(Order.OrderStatusEnum.SUCCESS.getValue());
        order.setIsUsed(SysConstants.IS_TRUE);
        when(orderMapper.selectById(7L)).thenReturn(order);

        OrderServiceImpl orderService = orderService(orderMapper);
        ReflectionTestUtils.setField(orderService, "orderDetailService", orderDetailService);
        ReflectionTestUtils.setField(orderService, "orderLogService", orderLogService);

        Map result = orderService.refund(11L, "游客临时取消");

        assertEquals("已操作退款，请勿重复操作", result.get(SysConstants.MSG));
        ArgumentCaptor<OrderLog> logCaptor = ArgumentCaptor.forClass(OrderLog.class);
        verify(orderLogService).saveLog(logCaptor.capture());
        OrderLog orderLog = logCaptor.getValue();
        assertEquals(OrderLog.ACTION_REFUND_APPLY, orderLog.getBizAction());
        assertEquals(SysConstants.IS_FALSE, orderLog.getSuccess());
        assertTrue(orderLog.getRequestContent().contains("orderDetailId"));
        assertTrue(orderLog.getRequestContent().contains("游客临时取消"));
    }

    @Test
    void refundAllRecordsOperationWhenOrderMissing() throws Exception {
        OrderMapper orderMapper = mock(OrderMapper.class);
        when(orderMapper.selectOne(any())).thenReturn(null);
        OrderLogService orderLogService = mock(OrderLogService.class);

        OrderServiceImpl orderService = orderService(orderMapper);
        ReflectionTestUtils.setField(orderService, "orderLogService", orderLogService);

        Map result = orderService.refundAll("202606170002", "管理员手动全退");

        assertEquals("订单不存在", result.get(SysConstants.MSG));
        ArgumentCaptor<OrderLog> logCaptor = ArgumentCaptor.forClass(OrderLog.class);
        verify(orderLogService).saveLog(logCaptor.capture());
        OrderLog orderLog = logCaptor.getValue();
        assertEquals("202606170002", orderLog.getOrderNo());
        assertEquals(OrderLog.ACTION_REFUND_APPLY, orderLog.getBizAction());
        assertEquals(SysConstants.IS_FALSE, orderLog.getSuccess());
        assertTrue(orderLog.getRequestContent().contains("refundAll"));
        assertTrue(orderLog.getRequestContent().contains("管理员手动全退"));
    }

    private OrderServiceImpl orderService(OrderMapper orderMapper) {
        OrderServiceImpl orderService = new OrderServiceImpl();
        ReflectionTestUtils.setField(orderService, "baseMapper", orderMapper);
        return orderService;
    }

    private OrderServiceImpl orderServiceWithPassingSlotLock(OrderMapper orderMapper) {
        OrderServiceImpl orderService = orderService(orderMapper);
        DistributedLockHandler distributedLockHandler = mock(DistributedLockHandler.class);
        when(distributedLockHandler.tryLock(any(), anyLong(), anyLong(), anyLong())).thenReturn(true);
        ReflectionTestUtils.setField(orderService, "distributedLockHandler", distributedLockHandler);
        return orderService;
    }

    private AppointmentVO baseAppointment() {
        AppointmentVO vo = new AppointmentVO();
        vo.setMuseumId(3L);
        vo.setOpenId("open-id");
        vo.setMoney(300);
        vo.setAppointmentDate(LocalDate.now());

        AppointmentVO.AppointmentDetailVO detailVO = new AppointmentVO.AppointmentDetailVO();
        detailVO.setActivityManageId(11L);
        detailVO.setActivityScheduleId(22L);
        detailVO.setNum(1);
        List<AppointmentVO.AppointmentDetailVO> details = new ArrayList<>();
        details.add(detailVO);
        vo.setList(details);
        return vo;
    }
}
