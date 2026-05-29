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
 * 订单详情表
 * </p>
 *
 * @author Cuicui
 * @since 2026-05-29
 */
@Data
@EqualsAndHashCode(callSuper = false)
@ApiModel(value = "OrderDetail对象", description = "订单详情表")
public class OrderDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "主键ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "订单ID")
    private Long orderId;

    @ApiModelProperty(value = "订单号")
    private String orderNo;

    @ApiModelProperty(value = "博物馆ID")
    private Long museumId;

    @ApiModelProperty(value = "活动ID")
    private Long activityId;

    @ApiModelProperty(value = "订单金额")
    private Integer orderAmount;

    @ApiModelProperty(value = "退款金额")
    private Integer refundAmount;

    @ApiModelProperty(value = "退款时间")
    private LocalDateTime refundTime;

    @ApiModelProperty(value = "退款原因")
    private String refundReason;

    @ApiModelProperty(value = "订单状态 -1：放弃支付；0：初始状态 10：支付成功；-2：退款；-11：退款中")
    private Integer orderStatus;

    @ApiModelProperty(value = "是否删除")
    private Integer isDeleted;

    @ApiModelProperty(value = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @ApiModelProperty(value = "更新时间")
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;

    @ApiModelProperty(value = "退款订单id")
    private String refundId;


    public static final String ID = "id";

    public static final String ORDER_ID = "order_id";

    public static final String ORDER_NO = "order_no";

    public static final String MUSEUM_ID = "museum_id";

    public static final String ACTIVITY_ID = "activity_id";

    public static final String ORDER_AMOUNT = "order_amount";

    public static final String REFUND_AMOUNT = "refund_amount";

    public static final String REFUND_TIME = "refund_time";

    public static final String REFUND_REASON = "refund_reason";

    public static final String ORDER_STATUS = "order_status";

    public static final String IS_DELETED = "is_deleted";

    public static final String CREATE_TIME = "create_time";

    public static final String UPDATE_TIME = "update_time";

    public static final String REFUND_ID = "refund_id";

    public enum OrderDetailStatusEnum {
        /**
         * 订单详情状态枚举
         * 订单状态 -1：放弃支付；0：初始状态 10：支付成功；-2：退款；-11：退款中
         */
        ABANDON(-1),
        INIT(0),
        PAY_SUCCESS(10),
        REFUND(-2),
        REFUNDING(-11);
        private Integer value;

        OrderDetailStatusEnum(Integer value) {
            this.value = value;
        }

        public Integer getValue() {
            return value;
        }
    }

}
