package com.cui.edu.trip.controller;


import cn.dev33.satoken.annotation.SaIgnore;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.write.handler.AbstractSheetWriteHandler;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.excel.write.metadata.holder.WriteSheetHolder;
import com.alibaba.excel.write.metadata.holder.WriteWorkbookHolder;
import com.cui.edu.common.HttpResult;
import com.cui.edu.system.service.MuseumService;
import com.cui.edu.trip.entity.unionpay.UnionPayDataV2;
import com.cui.edu.trip.entity.unionpay.UnionPayData;
import com.cui.edu.trip.service.ReconciliationService;
import com.cui.edu.trip.service.UnionPayDataService;
import com.cui.edu.util.easyexcel.listener.UnionPayDataListener;
import com.cui.edu.util.easyexcel.listener.UnionPayDataV2Listener;
import com.cui.edu.vo.reconciliation.ReconciliationAbnormalQueryVO;
import com.cui.edu.vo.reconciliation.ReconciliationAbnormalResult;
import com.cui.edu.vo.reconciliation.ReconciliationVO;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 银联excel表格数据 前端控制器
 * </p>
 *
 * @author Cuicui
 * @since 2026-07-15
 */
@RestController
@RequestMapping("/trip/union-pay-data")
public class UnionPayDataController {

    @Autowired
    private UnionPayDataService unionPayDataService;

    @Autowired
    private MuseumService museumService;

    @Autowired
    private ReconciliationService reconciliationService;

    @PostMapping("/uploadUnionPay")
    @ApiOperation(value = "银联excel导入（自动兼容格式一和格式二）")
    @SaIgnore // TODO 调试对账时去掉权限
    public HttpResult uploadUnionPay(MultipartFile file) throws IOException {
        byte[] fileBytes = file.getBytes();
        if (isUnionPayFormatV2(fileBytes)) {
            // 格式二：清算日期 | 交易日期时间 | 卡号 | 商编 | 终端 | 参考号 | 交易类型 | 交易金额 | 手续费 | 交易方式 | 订单号 | 商户名称
            EasyExcel.read(new ByteArrayInputStream(fileBytes), UnionPayDataV2.class,
                    new UnionPayDataV2Listener(unionPayDataService, museumService)).sheet().doRead();
        } else {
            // 格式一：商户名称 | 商户号 | 终端号 | 消费日期 | 交易时间 | 卡号 | 金额 | 手续费 | 净额 | 参考号 | 交易类型 | 交易渠道 | 商户订单号 | 银商订单号
            EasyExcel.read(new ByteArrayInputStream(fileBytes), UnionPayData.class,
                    new UnionPayDataListener(unionPayDataService, museumService)).sheet().doRead();
        }
        return HttpResult.ok();
    }

    /**
     * 读取 Excel 首行第二个单元格，判断是否为格式二（第二列为"交易日期时间"）
     * 格式一第二列为"商户号"，格式二第二列为"交易日期时间"
     */
    private boolean isUnionPayFormatV2(byte[] fileBytes) {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(fileBytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row firstRow = sheet.getRow(0);
            if (firstRow == null) {
                return false;
            }
            Cell secondCell = firstRow.getCell(1);
            if (secondCell == null) {
                return false;
            }
            // 格式二第二列为"交易日期时间"，格式一第二列为"商户号"
            String secondHeader = secondCell.getStringCellValue().trim();
            return "交易日期时间".equals(secondHeader);
        } catch (Exception e) {
            return false;
        }
    }

    @PostMapping(value = "/reconciliation/abnormal")
    @ApiOperation(value = "新异常对账（按分类返回详细数据）")
    @SaIgnore // TODO 调试对账时去掉权限
    public HttpResult reconciliationAbnormal(@RequestBody ReconciliationAbnormalQueryVO vo) {
        // 新接口接收博物馆及核对起止日期，不分页；日期先后关系由对账服务统一校验。
        if (vo == null || ObjectUtil.isEmpty(vo.getMuseumId())
                || vo.getStartDate() == null || vo.getEndDate() == null) {
            return HttpResult.errorBadRequest();
        }
        // 返回固定异常分类、分类明细、原始数据覆盖情况以及最终金额闭环结果。
        ReconciliationAbnormalResult result = reconciliationService.findAbnormalData(vo);
        return HttpResult.ok(result);
    }

    @PostMapping(value = "/reconciliation/export")
    @ApiOperation(value = "导出对账账单（租赁信息、退款信息、每日租赁数据）")
    @SaIgnore // TODO 调试对账时去掉权限
    public void exportReconciliation(@RequestBody ReconciliationAbnormalQueryVO vo,
                                     HttpServletResponse response) throws IOException {
        ReconciliationAbnormalResult result = reconciliationService.findAbnormalData(vo);
        String filename = "对账账单_" + result.getStartDate() + "_" + result.getEndDate() + ".xlsx";
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8.name())
                .replace("+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFilename);

        ExcelWriter writer = EasyExcel.write(response.getOutputStream()).build();
        try {
            writer.write(result.getRentalExportDetails(), exportSheet(0, "租赁信息",
                    ReconciliationAbnormalResult.RentalExportDetail.class, result.getRentalExportDetails()));
            writer.write(result.getRefundExportDetails(), exportSheet(1, "退款信息",
                    ReconciliationAbnormalResult.RefundExportDetail.class, result.getRefundExportDetails()));
            writer.write(result.getDailyRentalExportDetails(), exportSheet(2, "每日租赁数据",
                    ReconciliationAbnormalResult.DailyRentalExportDetail.class,
                    result.getDailyRentalExportDetails()));
        } finally {
            writer.finish();
        }
    }

    private <T> WriteSheet exportSheet(int sheetNo, String sheetName, Class<T> rowType, List<T> rows) {
        int[] columnWidths = measureColumnWidths(rowType, rows);
        return EasyExcel.writerSheet(sheetNo, sheetName).head(rowType)
                .registerWriteHandler(new DataBasedColumnWidthStyleStrategy(columnWidths)).build();
    }

    private <T> int[] measureColumnWidths(Class<T> rowType, List<T> rows) {
        List<Field> columns = Arrays.stream(rowType.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(ExcelProperty.class))
                .collect(Collectors.toList());
        int[] widths = new int[columns.size()];
        for (int index = 0; index < columns.size(); index++) {
            Field field = columns.get(index);
            field.setAccessible(true);
            String[] headers = field.getAnnotation(ExcelProperty.class).value();
            String header = headers.length == 0 ? "" : headers[0];
            int width = displayWidth(header);
            for (T row : rows) {
                try {
                    width = Math.max(width, displayWidth(displayValue(field.get(row))));
                } catch (IllegalAccessException exception) {
                    throw new IllegalStateException("无法读取账单列内容：" + field.getName(), exception);
                }
            }
            // 时间值以文本写入，给完整的yyyy-MM-dd HH:mm:ss预留空间，日期列也保留固定可读宽度。
            if (header.contains("时间")) {
                width = Math.max(width, 28);
            } else if ("日期".equals(header)) {
                width = Math.max(width, 14);
            }
            // 留出单元格内边距，避免刚好贴边；Excel 单列宽度最大为255字符。
            widths[index] = Math.max(10, Math.min(255, width + 4));
        }
        return widths;
    }

    private String displayValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private int displayWidth(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        return value.codePoints().map(codePoint -> codePoint > 0xFF ? 2 : 1).sum();
    }

    private static class DataBasedColumnWidthStyleStrategy extends AbstractSheetWriteHandler {
        private final int[] columnWidths;

        private DataBasedColumnWidthStyleStrategy(int[] columnWidths) {
            this.columnWidths = columnWidths;
        }

        @Override
        public void afterSheetCreate(WriteWorkbookHolder writeWorkbookHolder, WriteSheetHolder writeSheetHolder) {
            for (int index = 0; index < columnWidths.length; index++) {
                writeSheetHolder.getSheet().setColumnWidth(index, columnWidths[index] * 256);
            }
        }
    }


    @PostMapping("/billing")
    @ApiOperation(value = "各个博物馆的账单（已废弃）",
            notes = "请使用/reconciliation/abnormal响应中的billing字段")
    @Deprecated
    public HttpResult billing(@RequestBody ReconciliationVO vo) {
        if (BeanUtil.isNotEmpty(vo)) {
            List<Map> result = unionPayDataService.billing(vo);
            return HttpResult.ok(result);
        } else {
            return HttpResult.errorBadRequest();
        }
    }

    @PostMapping(value = "/abnormalData")
    @ApiOperation(value = "异常数据（已废弃）", notes = "请使用/reconciliation/abnormal")
    @Deprecated
    public HttpResult abnormalData(@RequestBody ReconciliationVO vo) {
        if (BeanUtil.isNotEmpty(vo)) {
            Map<String, Collection> map = unionPayDataService.abnormalData(vo);
            return HttpResult.ok(map);
        } else {
            return HttpResult.errorBadRequest();
        }
    }

    @GetMapping(value = "/detail/{tradeNo}/{museumId}")
    @ApiOperation(value = "订单详情（已废弃）", notes = "新异常接口已直接返回主订单、全部子订单及银联流水")
    @Deprecated
    public HttpResult detail(@ApiParam(value = "银联订单号") @PathVariable String tradeNo, @ApiParam(value = "博物馆id") @PathVariable String museumId) {
        if (ObjectUtil.isNotEmpty(tradeNo)) {
            Map map = unionPayDataService.detail(tradeNo, museumId);
            return HttpResult.ok(map);
        } else {
            return HttpResult.errorBadRequest();
        }
    }

}
