package com.cui.edu.trip.entity;


import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.io.Serializable;
import java.util.List;

import com.cui.edu.system.entity.Museum;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>
 * 订单表
 * </p>
 *
 * @author Cuicui
 * @since 2026-05-29
 */
@Data
@EqualsAndHashCode(callSuper = false)
@ApiModel(value = "Order对象", description = "订单表")
@TableName("`order`")
public class Order implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "主键ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "订单号")
    private String orderNo;

    @ApiModelProperty(value = "博物馆ID")
    private Long museumId;

    @ApiModelProperty(value = "订单类型（1：个人；2：团队）")
    private Integer orderType;

    @ApiModelProperty(value = "支付金额")
    private Integer payAmount;

    @ApiModelProperty(value = "订单数量")
    private Integer orderQuantity;

    @ApiModelProperty(value = "订单状态 1：支付中 10：支付成功；-1：放弃支付；-2：部分退款；-10：全额退款；-11：退款中")
    private Integer orderStatus;

    @ApiModelProperty(value = "银联单号")
    private String unionpayOrderNo;

    @ApiModelProperty(value = "是否使用：1已使用 0未使用")
    private Integer isUsed;

    @ApiModelProperty(value = "核销时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime verificationTime;

    @ApiModelProperty(value = "退款金额")
    private Integer refundAmount;

    @ApiModelProperty(value = "退款数量")
    private Integer refundQuantity;

    @ApiModelProperty(value = "小程序支付参数")
    private String miniProgramPayParams;

    @ApiModelProperty(value = "团队ID")
    private Long teamId;

    @ApiModelProperty(value = "游客批次号")
    private String batchNo;

    @ApiModelProperty(value = "游客ID")
    private Long visitorId;

    @ApiModelProperty(value = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @ApiModelProperty(value = "更新时间")
    @TableField(fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    @ApiModelProperty(value = "预约日期")
    private LocalDate appointmentDate;

    @ApiModelProperty(value = "支付成功时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime paySuccessTime;

    @ApiModelProperty(value = "博物馆信息")
    @TableField(exist = false)
    private Museum museum;

    @ApiModelProperty(value = "游客信息")
    @TableField(exist = false)
    private Visitor visitor;

    @ApiModelProperty(value = "团队信息")
    @TableField(exist = false)
    private Team team;

    @ApiModelProperty(value = "子订单列表")
    @TableField(exist = false)
    private List<OrderDetail> detailList;

    @ApiModelProperty(value = "是否已评价：1已评价；0未评价")
    @TableField(exist = false)
    private Integer isEvaluated;


    public static final String ID = "id";

    public static final String ORDER_NO = "order_no";

    public static final String MUSEUM_ID = "museum_id";

    public static final String ORDER_TYPE = "order_type";

    public static final String PAY_AMOUNT = "pay_amount";

    public static final String ORDER_QUANTITY = "order_quantity";

    public static final String ORDER_STATUS = "order_status";

    public static final String UNIONPAY_ORDER_NO = "unionpay_order_no";

    public static final String IS_USED = "is_used";

    public static final String VERIFICATION_TIME = "verification_time";

    public static final String REFUND_AMOUNT = "refund_amount";

    public static final String REFUND_QUANTITY = "refund_quantity";

    public static final String MINI_PROGRAM_PAY_PARAMS = "mini_program_pay_params";

    public static final String TEAM_ID = "team_id";

    public static final String BATCH_NO = "batch_no";

    public static final String VISITOR_ID = "visitor_id";

    public static final String CREATE_TIME = "create_time";

    public static final String UPDATE_TIME = "update_time";

    public static final String APPOINTMENT_DATE = "appointment_date";

    public static final String PAY_SUCCESS_TIME = "pay_success_time";

    /**
     * 订单状态枚举
     * 1：支付中；10：支付成功；-1：放弃支付；-2：部分退款；-10：全额退款；-11：退款中
     */
    public enum OrderStatusEnum {
        PAYING(1),
        SUCCESS(10),
        ABANDON(-1),
        PARTIAL_REFUND(-2),
        ALL_REFUND(-10),
        REFUNDING(-11);
        private Integer value;

        OrderStatusEnum(Integer value) {
            this.value = value;
        }

        public Integer getValue() {
            return value;
        }
    }
}
