package com.cui.edu.trip.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cui.edu.system.entity.Museum;
import com.cui.edu.system.service.MuseumService;
import com.cui.edu.trip.entity.unionpay.UnionPayData;
import com.cui.edu.trip.entity.Order;
import com.cui.edu.trip.mapper.OrderMapper;
import com.cui.edu.trip.mapper.OrderDetailMapper;
import com.cui.edu.trip.mapper.UnionPayDataMapper;
import com.cui.edu.trip.service.UnionPayDataService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cui.edu.vo.reconciliation.ReconciliationVO;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.cui.edu.util.DateTimeUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 银联excel表格数据 服务实现类
 * </p>
 *
 * @author Cuicui
 * @since 2026-07-15
 */
@Service
public class UnionPayDataServiceImpl extends ServiceImpl<UnionPayDataMapper, UnionPayData> implements UnionPayDataService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private UnionPayDataService unionPayDataService;

    @Autowired
    private MuseumService museumService;

    @Override
    public List<Map> billing(ReconciliationVO vo) {
        // 核对月的当月月初日期
        String currentMonthStart = vo.getStartDate();
        // 核对月的当月月末日期
        String currentMonthEnd = vo.getEndDate();
        List<Map> result = orderMapper.findNumAndAmount(vo.getMuseumId(), DateTimeUtils.getStartDateTimeString(currentMonthStart), DateTimeUtils.getEndDateTimeString(currentMonthEnd));
        return result;
    }


    @Override
    public Map<String, Collection> abnormalData(ReconciliationVO vo) {
        Map<String, Collection> result = new HashMap(16);
        // 核对月的上月月初日期
        String lastMonthStart = DateTimeUtils.getFirstDayOfBeforeMonth(vo.getStartDate());
        // 核对月的上月月末日期
        String lastMonthEnd = DateTimeUtils.getLastDayOfBeforeMonth(vo.getEndDate());
        // 核对月的当月月初日期
        String currentMonthStart = vo.getStartDate();
        // 核对月的当月月末日期
        String currentMonthEnd = vo.getEndDate();
        // 核对月的下月月初日期
        String nextMonthStart = DateTimeUtils.getFirstDayOfMonth(vo.getStartDate());
        // 核对月的下月月末日期
        String nextMonthEnd = DateTimeUtils.getLastDayOfMonth(vo.getEndDate());

        // 博物馆信息
        Museum museum = museumService.getById(vo.getMuseumId());
        // 系统-支付
        List<String> verificationTradeNoList = orderMapper.findVerificationTradeNo(vo.getMuseumId(), DateTimeUtils.getStartDateTimeString(currentMonthStart), DateTimeUtils.getEndDateTimeString(currentMonthEnd));
        // 系统-退款
        List<String> verificationRefundTradeNoList = orderDetailMapper.findVerificationRefundTradeNo(vo.getMuseumId(), DateTimeUtils.getStartDateTimeString(currentMonthStart), DateTimeUtils.getEndDateTimeString(currentMonthEnd));
        Set<String> verificationRefundSet = new HashSet<>();
        for (String str : verificationRefundTradeNoList) {
            verificationRefundSet.add(str);
        }
        // 银联-支付
        QueryWrapper<UnionPayData> wrapperPay = new QueryWrapper<>();
        wrapperPay.eq(UnionPayData.MID, museum.getMid());
        wrapperPay.eq(UnionPayData.TID, museum.getTid());
        wrapperPay.eq(UnionPayData.TYPE, "消费");
        wrapperPay.between(UnionPayData.TRANSACTION_TIME, DateTimeUtils.getStartDateTime(currentMonthStart), DateTimeUtils.getEndDateTime(currentMonthEnd));
        List<UnionPayData> unionPayList = unionPayDataService.list(wrapperPay);
        List<String> unionPayTradeNos = new ArrayList<>();
        for (UnionPayData unionPay : unionPayList) {
            // 银联-商户订单号
            unionPayTradeNos.add(unionPay.getMerchantOrderNo());
        }
        // 银联-退款
        QueryWrapper<UnionPayData> wrapperRefund = new QueryWrapper<>();
        wrapperRefund.eq(UnionPayData.MID, museum.getMid());
        wrapperRefund.eq(UnionPayData.TID, museum.getTid());
        wrapperRefund.eq(UnionPayData.TYPE, "联机退货");
        wrapperRefund.between(UnionPayData.TRANSACTION_TIME, DateTimeUtils.getStartDateTime(currentMonthStart), DateTimeUtils.getEndDateTime(currentMonthEnd));
        List<UnionPayData> unionRefundList = unionPayDataService.list(wrapperRefund);
        Set<String> unionRefundTradeNos = new HashSet<>();
        List<String> unionRefundTradeNosList = new ArrayList<>();
        for (UnionPayData unionPay : unionRefundList) {
            // 银联-商户订单号
            unionRefundTradeNos.add(unionPay.getMerchantOrderNo());
            unionRefundTradeNosList.add(unionPay.getMerchantOrderNo());
        }
        // 拿系统核销去跟银联-支付取差集，得出系统核销有的单子，银联没有
        Collection subtract1 = CollectionUtils.subtract(verificationTradeNoList, unionPayTradeNos);
        if (subtract1 != null && subtract1.size() > 0) {
            result.put("有核销订单，但银联没有支付", subtract1);
        }
        // 拿银联-支付去跟系统核销取差集，得出银联有，系统核销没有的单子
        Collection subtract2 = CollectionUtils.subtract(unionPayTradeNos, verificationTradeNoList);
        if (subtract2 != null && subtract2.size() > 0) {
            // 先排除退款的订单
            Collection subtract4 = CollectionUtils.subtract(subtract2, unionRefundTradeNos);
            if (ObjectUtil.isNotEmpty(subtract4)) {
                result.put("银联有支付记录，我们没有有效订单", subtract4);
            }
        }
        // 拿银联退去跟系统退取差集，得出银联有，系统没有
        Collection subtract6 = CollectionUtils.subtract(unionRefundTradeNos, verificationRefundTradeNoList);
        Collection subtract9 = CollectionUtils.intersection(subtract6, verificationTradeNoList);
        if (ObjectUtil.isNotEmpty(subtract9)) {
            result.put("银联有退款，我们没有，但有核销记录", subtract9);
        }
        // 拿系统退去跟银联-退取差集，得出系统有，银联没有
        Collection subtract8 = CollectionUtils.subtract(verificationRefundTradeNoList, unionRefundTradeNosList);
        if (ObjectUtil.isNotEmpty(subtract8)) {
            List<String> refundAmountException = new ArrayList<>();
            subtract8.forEach(s -> {
                if (Objects.nonNull(s)) {
                    // 判断退款金额是否一致，一致的话，不是异常单
                    QueryWrapper<Order> queryWrapper = new QueryWrapper<>();
                    queryWrapper.eq(Order.UNIONPAY_ORDER_NO, s.toString());
                    Order order = orderMapper.selectOne(queryWrapper);
                    BigDecimal orderRefundAmount = new BigDecimal(order.getRefundAmount())
                            .divide(new BigDecimal(100))
                            .setScale(2, RoundingMode.HALF_UP); // 保留两位小数，四舍五入


                    QueryWrapper<UnionPayData> wrapperUnionRefund = new QueryWrapper<>();
                    wrapperUnionRefund.eq(UnionPayData.TYPE, "联机退货");
                    wrapperUnionRefund.eq(UnionPayData.MERCHANT_ORDER_NO, s.toString());
                    List<UnionPayData> refundData = unionPayDataService.list(wrapperUnionRefund);
                    BigDecimal unionRefundAmount = refundData.stream().map(UnionPayData::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                    unionRefundAmount = unionRefundAmount.abs();

                    if (orderRefundAmount.compareTo(unionRefundAmount) != 0) {
                        refundAmountException.add(s.toString());
                    }
                }
            });
            if (ObjectUtil.isNotEmpty(refundAmountException)) {
                result.put("我们有退款，银联没有", refundAmountException);
            }
        }
        // 银联退、系统退取交集，查询出退的金额不一致的情况
        List<String> refundIntersection = new ArrayList<>(CollectionUtils.intersection(verificationRefundSet, unionRefundTradeNos));
        List<String> refundAmountException = new ArrayList<>();
        if (ObjectUtil.isNotEmpty(refundIntersection)) {
            for (String str : refundIntersection) {
                QueryWrapper<UnionPayData> refund = new QueryWrapper<>();
                refund.eq(UnionPayData.MID, museum.getMid());
                refund.eq(UnionPayData.TID, museum.getTid());
                refund.eq(UnionPayData.TYPE, "联机退货");
                refund.eq(UnionPayData.MERCHANT_ORDER_NO, str);
                List<UnionPayData> refundData = unionPayDataService.list(refund);
                BigDecimal totalAmount = refundData.stream()
                        .map(UnionPayData::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                totalAmount = totalAmount.abs();

                QueryWrapper<Order> orderQuery = new QueryWrapper<>();
                orderQuery.eq(Order.UNIONPAY_ORDER_NO, str);
                Order order = orderMapper.selectOne(orderQuery);

                if (!order.getRefundAmount().equals(totalAmount.multiply(new BigDecimal(100)).intValue())) {
                    refundAmountException.add(str);
                }
            }
        }
        if (ObjectUtil.isNotEmpty(refundAmountException)) {
            result.put("退款金额不一致", refundAmountException);
        }
        // 上月订单且上月核销之后，核对月退
        List<String> acrossMonthsUsedRefundList = orderDetailMapper.findAcrossMonthsUsedRefund(DateTimeUtils.getStartDateTimeString(lastMonthStart), DateTimeUtils.getEndDateTimeString(lastMonthEnd), DateTimeUtils.getStartDateTimeString(currentMonthStart), DateTimeUtils.getEndDateTimeString(currentMonthEnd), vo.getMuseumId());
        if (acrossMonthsUsedRefundList != null && acrossMonthsUsedRefundList.size() > 0) {
            result.put("上月订单且上月核销之后，核对月退，这种情况有时候要观察上月这个订单是否退过。", acrossMonthsUsedRefundList);
        }
        List<String> acrossMultipleMonthsUsedRefundList = orderDetailMapper.findAcrossMultipleMonthsUsedRefund(DateTimeUtils.getStartDateTimeString(currentMonthStart), DateTimeUtils.getEndDateTimeString(currentMonthEnd), vo.getMuseumId());
        if (acrossMultipleMonthsUsedRefundList != null && acrossMultipleMonthsUsedRefundList.size() > 0) {
            result.put("跨月订单核销之后，核对月退，会和上月订单核对月退重合，但是增加了跨多月退的情况", acrossMultipleMonthsUsedRefundList);
        }
        // 核对月订单且核对月核销之后，下月退
        List<String> acrossMonthsUsedRefundList2 = orderDetailMapper.findAcrossMonthsUsedRefund(DateTimeUtils.getStartDateTimeString(currentMonthStart), DateTimeUtils.getEndDateTimeString(currentMonthEnd), DateTimeUtils.getStartDateTimeString(nextMonthStart), DateTimeUtils.getEndDateTimeString(nextMonthEnd), vo.getMuseumId());
        if (acrossMonthsUsedRefundList2 != null && acrossMonthsUsedRefundList2.size() > 0) {
            result.put("核对月订单且核对月核销之后，下月退，这种情况有时候要观察核对月这个订单是否退过。", acrossMonthsUsedRefundList2);
        }
        // 核对月核对月支付但预约日期在下月的订单（跨月预约）
        List<String> crossMonthAppointmentList = orderMapper.findCrossMonthAppointmentTradeNo(vo.getMuseumId(), currentMonthStart, currentMonthEnd);
        if (crossMonthAppointmentList != null && !crossMonthAppointmentList.isEmpty()) {
            result.put("核对月支付但预约日期在下月的订单（跨月预约）", crossMonthAppointmentList);
        }
        return result;
    }

    @Override
    public Map detail(String tradeNo, String museumId) {
        Museum museum = museumService.getById(museumId);
        Map result = new HashMap(4);
        // 查银联
        QueryWrapper<UnionPayData> qw = new QueryWrapper<>();
        qw.eq(UnionPayData.MERCHANT_ORDER_NO, tradeNo);
        qw.eq(UnionPayData.TYPE, "消费");
        UnionPayData unionPayData = unionPayDataService.getOne(qw);
        result.put("unionPayData", unionPayData);

        qw.clear();
        qw.eq(UnionPayData.MERCHANT_ORDER_NO, tradeNo);
        qw.eq(UnionPayData.TYPE, "联机退货");
        List<UnionPayData> unionPayDataRefund = unionPayDataService.list(qw);
        result.put("unionPayDataRefund", unionPayDataRefund);
        // 查系统
        QueryWrapper<Order> orderQueryWrapper = new QueryWrapper<>();
        orderQueryWrapper.eq(Order.UNIONPAY_ORDER_NO, tradeNo);
        Order order = orderMapper.selectOne(orderQueryWrapper);
        result.put("order", order);
        List<Map> orderDetailList = new ArrayList<>();
        if (BeanUtil.isNotEmpty(order)) {
            orderDetailList = orderDetailMapper.findListByOrderId(order.getId());
        }
        result.put("orderDetailList", orderDetailList);
        return result;
    }
}
