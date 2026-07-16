package com.cui.edu.trip.entity.unionpay;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 银联 Excel 格式二：清算日期、交易日期时间、卡号、商编、终端、参考号、交易类型、交易金额、手续费、交易方式、订单号、商户名称
 */
@Data
public class UnionPayDataV2 implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 清算日期，格式 yyyyMMdd，如 20260601 */
    @ExcelProperty("清算日期")
    private String settlementDate;

    /** 交易日期时间，格式 HHmmss，如 101136 */
    @ExcelProperty("交易日期时间")
    private String transactionTime;

    @ExcelProperty("卡号")
    private String cardNo;

    /** 商编（对应格式一的商户号） */
    @ExcelProperty("商编")
    private String mid;

    /** 终端（对应格式一的终端号） */
    @ExcelProperty("终端")
    private String tid;

    @ExcelProperty("参考号")
    private String referenceNumber;

    @ExcelProperty("交易类型")
    private String type;

    /** 交易金额（对应格式一的金额） */
    @ExcelProperty("交易金额")
    private BigDecimal amount;

    @ExcelProperty("手续费")
    private BigDecimal handlingCharge;

    /** 交易方式（如微信、支付宝等，对应格式一的交易渠道） */
    @ExcelProperty("交易方式")
    private String channel;

    /** 订单号（对应格式一的商户订单号） */
    @ExcelProperty("订单号")
    private String merchantOrderNo;

    /** 商户名称 */
    @ExcelProperty("商户名称")
    private String merchantName;
}
