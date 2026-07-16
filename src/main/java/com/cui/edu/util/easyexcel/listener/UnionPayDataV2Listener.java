package com.cui.edu.util.easyexcel.listener;

import cn.hutool.core.util.ObjectUtil;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.util.ListUtils;
import com.cui.edu.system.entity.Museum;

import com.cui.edu.system.service.MuseumService;
import com.cui.edu.trip.entity.unionpay.UnionPayData;
import com.cui.edu.trip.entity.unionpay.UnionPayDataV2;
import com.cui.edu.trip.service.UnionPayDataService;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 银联 Excel 格式二 Listener
 * 表头格式：清算日期 | 交易日期时间 | 卡号 | 商编 | 终端 | 参考号 | 交易类型 | 交易金额 | 手续费 | 交易方式 | 订单号 | 商户名称
 */
@Slf4j
public class UnionPayDataV2Listener extends AnalysisEventListener<UnionPayDataV2> {

    private static final int BATCH_COUNT = 1000;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HHmmss");

    private List<UnionPayData> cachedDataList = ListUtils.newArrayListWithExpectedSize(BATCH_COUNT);

    private final UnionPayDataService unionPayDataService;
    private final MuseumService museumService;

    private List<Museum> museumList;
    private final List<String> mid = new ArrayList<>();
    private final List<String> tid = new ArrayList<>();

    public UnionPayDataV2Listener(UnionPayDataService unionPayDataService, MuseumService museumService) {
        this.unionPayDataService = unionPayDataService;
        this.museumService = museumService;
    }

    @Override
    public void invoke(UnionPayDataV2 v2, AnalysisContext analysisContext) {
        // 只解析自助租赁的数据
        if (ObjectUtil.isEmpty(museumList)) {
            museumList = museumService.list();
            List<String> midList = museumList.stream().map(Museum::getMid).filter(ObjectUtil::isNotEmpty).collect(Collectors.toList());
            List<String> tidList = museumList.stream().map(Museum::getTid).filter(ObjectUtil::isNotEmpty).collect(Collectors.toList());
            mid.addAll(midList);
            tid.addAll(tidList);
        }

        if (mid.contains(v2.getMid()) && tid.contains(v2.getTid())) {
            UnionPayData data = convertToUnionPayData(v2);
            cachedDataList.add(data);
            if (cachedDataList.size() >= BATCH_COUNT) {
                saveData();
                cachedDataList = ListUtils.newArrayListWithExpectedSize(BATCH_COUNT);
            }
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {
        saveData();
        log.info("所有数据解析完成！");
    }

    private void saveData() {
        if (ObjectUtil.isEmpty(cachedDataList)) {
            return;
        }
        log.info("{}条数据，开始存储数据库！", cachedDataList.size());
        unionPayDataService.saveBatch(cachedDataList);
        log.info("存储数据库成功！");
    }

    /**
     * 将格式二的数据转换为统一的 UnionPayData
     * - settlementDate: "20260601" -> LocalDate
     * - transactionTime: "101136" -> LocalDateTime（结合清算日期）
     */
    private UnionPayData convertToUnionPayData(UnionPayDataV2 v2) {
        UnionPayData data = new UnionPayData();
        data.setMerchantName(v2.getMerchantName());
        data.setMid(v2.getMid());
        data.setTid(v2.getTid());
        data.setCardNo(v2.getCardNo());
        data.setAmount(v2.getAmount());
        data.setHandlingCharge(v2.getHandlingCharge());
        data.setReferenceNumber(v2.getReferenceNumber());
        data.setType(v2.getType());
        data.setChannel(v2.getChannel());
        data.setMerchantOrderNo(v2.getMerchantOrderNo());
        // 净额 = 交易金额 - 手续费
        if (v2.getAmount() != null && v2.getHandlingCharge() != null) {
            data.setNetAmount(v2.getAmount().subtract(v2.getHandlingCharge()));
        }
        // 银商订单号在格式二中没有，留空
        data.setOrderNo(null);

        // 解析清算日期
        if (ObjectUtil.isNotEmpty(v2.getSettlementDate())) {
            try {
                data.setSettlementDate(LocalDate.parse(v2.getSettlementDate().trim(), DATE_FORMATTER));
            } catch (Exception e) {
                log.warn("清算日期解析失败，原始值：{}", v2.getSettlementDate());
            }
        }

        // 解析交易时间（格式二的 transactionTime 仅有时分秒，需要结合清算日期组成完整的 LocalDateTime）
        if (ObjectUtil.isNotEmpty(v2.getTransactionTime()) && data.getSettlementDate() != null) {
            try {
                String timeStr = String.format("%06d", Long.parseLong(v2.getTransactionTime().trim()));
                LocalDateTime ldt = LocalDateTime.of(data.getSettlementDate(),
                        java.time.LocalTime.parse(timeStr, TIME_FORMATTER));
                data.setTransactionTime(ldt);
            } catch (Exception e) {
                log.warn("交易时间解析失败，原始值：{}", v2.getTransactionTime());
            }
        }

        return data;
    }
}
