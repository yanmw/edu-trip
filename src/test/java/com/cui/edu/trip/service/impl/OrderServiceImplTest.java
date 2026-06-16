package com.cui.edu.trip.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cui.edu.common.PageResult;
import com.cui.edu.trip.entity.Order;
import com.cui.edu.trip.mapper.OrderMapper;
import com.cui.edu.trip.service.OrderDetailService;
import com.cui.edu.vo.trip.OrderVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
        assertTrue(sqlSegment.contains("appointment_date"));
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

    private OrderServiceImpl orderService(OrderMapper orderMapper) {
        OrderServiceImpl orderService = new OrderServiceImpl();
        ReflectionTestUtils.setField(orderService, "baseMapper", orderMapper);
        return orderService;
    }
}
