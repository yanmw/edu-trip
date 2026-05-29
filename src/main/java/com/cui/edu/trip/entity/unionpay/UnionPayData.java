package com.cui.edu.trip.entity.unionpay;

import com.alibaba.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * <p>
 * 银联excel表格数据
 * </p>
 *
 * @author Cuicui
 * @since 2024-09-14
 */
@Data
@EqualsAndHashCode(callSuper = false)
@ApiModel(value="UnionPayData对象", description="银联excel表格数据")
public class UnionPayData implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "id")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "商户名称")
    @ExcelProperty("商户名称")
    private String merchantName;

    @ApiModelProperty(value = "商户号")
    @ExcelProperty("商户号")
    private String mid;

    @ApiModelProperty(value = "终端号")
    @ExcelProperty("终端号")
    private String tid;

    @ApiModelProperty(value = "结算日期")
    @ExcelProperty("结算日期")
    private LocalDate settlementDate;

    @ApiModelProperty(value = "交易时间")
    @ExcelProperty("交易时间")
    private LocalDateTime transactionTime;

    @ApiModelProperty(value = "卡号")
    @ExcelProperty("卡号")
    private String cardNo;

    @ApiModelProperty(value = "金额")
    @ExcelProperty("金额")
    private BigDecimal amount;

    @ApiModelProperty(value = "手续费")
    @ExcelProperty("手续费")
    private BigDecimal handlingCharge;

    @ApiModelProperty(value = "净额")
    @ExcelProperty("净额")
    private BigDecimal netAmount;

    @ApiModelProperty(value = "参考号")
    @ExcelProperty("参考号")
    private String referenceNumber;

    @ApiModelProperty(value = "交易类型")
    @ExcelProperty("交易类型")
    private String type;

    @ApiModelProperty(value = "交易渠道")
    @ExcelProperty("交易渠道")
    private String channel;

    @ApiModelProperty(value = "商户订单号")
    @ExcelProperty("商户订单号")
    private String merchantOrderNo;

    @ApiModelProperty(value = "银商订单号")
    @ExcelProperty("银商订单号")
    private String orderNo;


    public static final String ID = "id";

    public static final String MERCHANT_NAME = "merchant_name";

    public static final String MID = "mid";

    public static final String TID = "tid";

    public static final String SETTLEMENT_DATE = "settlement_date";

    public static final String TRANSACTION_TIME = "transaction_time";

    public static final String CARD_NO = "card_no";

    public static final String AMOUNT = "amount";

    public static final String HANDLING_CHARGE = "handling_charge";

    public static final String NET_AMOUNT = "net_amount";

    public static final String REFERENCE_NUMBER = "reference_number";

    public static final String TYPE = "type";

    public static final String CHANNEL = "channel";

    public static final String MERCHANT_ORDER_NO = "merchant_order_no";

    public static final String ORDER_NO = "order_no";

}
