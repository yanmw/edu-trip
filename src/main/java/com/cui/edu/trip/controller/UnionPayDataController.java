package com.cui.edu.trip.controller;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.excel.EasyExcel;
import com.cui.edu.common.HttpResult;
import com.cui.edu.system.service.MuseumService;
import com.cui.edu.trip.entity.unionpay.UnionPayDataV2;
import com.cui.edu.trip.entity.unionpay.UnionPayData;
import com.cui.edu.trip.service.UnionPayDataService;
import com.cui.edu.util.easyexcel.listener.UnionPayDataListener;
import com.cui.edu.util.easyexcel.listener.UnionPayDataV2Listener;
import com.cui.edu.vo.reconciliation.ReconciliationVO;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

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

    @PostMapping("/uploadUnionPay")
    @ApiOperation(value = "银联excel导入（自动兼容格式一和格式二）")
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

    @PostMapping("/billing")
    @ApiOperation(value = "各个博物馆的账单")
    public HttpResult billing(@RequestBody ReconciliationVO vo) {
        if (BeanUtil.isNotEmpty(vo)) {
            List<Map> result = unionPayDataService.billing(vo);
            return HttpResult.ok(result);
        } else {
            return HttpResult.errorBadRequest();
        }
    }

    @PostMapping(value = "/abnormalData")
    @ApiOperation(value = "异常数据")
    public HttpResult abnormalData(@RequestBody ReconciliationVO vo) {
        if (BeanUtil.isNotEmpty(vo)) {
            Map<String, Collection> map = unionPayDataService.abnormalData(vo);
            return HttpResult.ok(map);
        } else {
            return HttpResult.errorBadRequest();
        }
    }

    @GetMapping(value = "/detail/{tradeNo}/{museumId}")
    @ApiOperation(value = "订单详情")
    public HttpResult detail(@ApiParam(value = "银联订单号") @PathVariable String tradeNo, @ApiParam(value = "博物馆id") @PathVariable String museumId) {
        if (ObjectUtil.isNotEmpty(tradeNo)) {
            Map map = unionPayDataService.detail(tradeNo, museumId);
            return HttpResult.ok(map);
        } else {
            return HttpResult.errorBadRequest();
        }
    }

}
