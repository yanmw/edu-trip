package com.cui.edu.trip.entity;

import java.math.BigDecimal;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;

import java.time.LocalDateTime;
import java.io.Serializable;

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

    @ApiModelProperty(value = "退款金额")
    private Integer refundAmount;

    @ApiModelProperty(value = "退款数量")
    private Integer refundQuantity;

    @ApiModelProperty(value = "小程序支付参数")
    private String miniProgramPayParams;

    @ApiModelProperty(value = "团队ID")
    private Long teamId;

    @ApiModelProperty(value = "游客ID")
    private Long visitorId;

    @ApiModelProperty(value = "是否删除")
    private Integer isDeleted;

    @ApiModelProperty(value = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @ApiModelProperty(value = "更新时间")
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;


    public static final String ID = "id";

    public static final String ORDER_NO = "order_no";

    public static final String MUSEUM_ID = "museum_id";

    public static final String ORDER_TYPE = "order_type";

    public static final String PAY_AMOUNT = "pay_amount";

    public static final String ORDER_QUANTITY = "order_quantity";

    public static final String ORDER_STATUS = "order_status";

    public static final String UNIONPAY_ORDER_NO = "unionpay_order_no";

    public static final String IS_USED = "is_used";

    public static final String REFUND_AMOUNT = "refund_amount";

    public static final String REFUND_QUANTITY = "refund_quantity";

    public static final String MINI_PROGRAM_PAY_PARAMS = "mini_program_pay_params";

    public static final String TEAM_ID = "team_id";

    public static final String VISITOR_ID = "visitor_id";

    public static final String IS_DELETED = "is_deleted";

    public static final String CREATE_TIME = "create_time";

    public static final String UPDATE_TIME = "update_time";

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
