package com.cui.edu.trip.entity;

import java.math.BigDecimal;
import com.baomidou.mybatisplus.annotation.IdType;
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
@ApiModel(value="OrderDetail对象", description="订单详情表")
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

    @ApiModelProperty(value = "活动场次ID")
    private Long scheduleId;

    @ApiModelProperty(value = "订单金额")
    private BigDecimal orderAmount;

    @ApiModelProperty(value = "退款金额")
    private BigDecimal refundAmount;

    @ApiModelProperty(value = "退款时间")
    private LocalDateTime refundTime;

    @ApiModelProperty(value = "退款原因")
    private String refundReason;

    @ApiModelProperty(value = "订单状态")
    private Integer orderStatus;

    @ApiModelProperty(value = "是否删除")
    private Integer isDeleted;

    @ApiModelProperty(value = "创建时间")
    private LocalDateTime createTime;

    @ApiModelProperty(value = "更新时间")
    private LocalDateTime updateTime;


    public static final String ID = "id";

    public static final String ORDER_ID = "order_id";

    public static final String ORDER_NO = "order_no";

    public static final String MUSEUM_ID = "museum_id";

    public static final String ACTIVITY_ID = "activity_id";

    public static final String SCHEDULE_ID = "schedule_id";

    public static final String ORDER_AMOUNT = "order_amount";

    public static final String REFUND_AMOUNT = "refund_amount";

    public static final String REFUND_TIME = "refund_time";

    public static final String REFUND_REASON = "refund_reason";

    public static final String ORDER_STATUS = "order_status";

    public static final String IS_DELETED = "is_deleted";

    public static final String CREATE_TIME = "create_time";

    public static final String UPDATE_TIME = "update_time";

}
