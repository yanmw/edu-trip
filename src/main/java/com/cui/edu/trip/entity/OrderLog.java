package com.cui.edu.trip.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 订单日志表
 * </p>
 *
 * <p>该表只追加不更新，用于追踪订单状态变化和银联支付、退款交易过程。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("order_log")
@ApiModel(value = "OrderLog对象", description = "订单日志表")
public class OrderLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "主键ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "主订单ID")
    private Long orderId;

    @ApiModelProperty(value = "主订单号")
    private String orderNo;

    @ApiModelProperty(value = "子订单ID")
    private Long orderDetailId;

    @ApiModelProperty(value = "影响的子订单ID集合")
    private String affectedDetailIds;

    @ApiModelProperty(value = "博物馆ID")
    private Long museumId;

    @ApiModelProperty(value = "游客ID")
    private Long visitorId;

    @ApiModelProperty(value = "团队ID")
    private Long teamId;

    @ApiModelProperty(value = "日志类型：1订单状态；2支付交易；3退款交易；4核销；5补偿查询")
    private Integer logType;

    @ApiModelProperty(value = "业务动作")
    private String bizAction;

    @ApiModelProperty(value = "事件来源")
    private String eventSource;

    @ApiModelProperty(value = "变更前主订单状态")
    private Integer beforeOrderStatus;

    @ApiModelProperty(value = "变更后主订单状态")
    private Integer afterOrderStatus;

    @ApiModelProperty(value = "变更前子订单状态")
    private Integer beforeDetailStatus;

    @ApiModelProperty(value = "变更后子订单状态")
    private Integer afterDetailStatus;

    @ApiModelProperty(value = "变更前是否核销")
    private Integer beforeIsUsed;

    @ApiModelProperty(value = "变更后是否核销")
    private Integer afterIsUsed;

    @ApiModelProperty(value = "变更前退款金额")
    private Integer beforeRefundAmount;

    @ApiModelProperty(value = "变更后退款金额")
    private Integer afterRefundAmount;

    @ApiModelProperty(value = "变更前退款数量")
    private Integer beforeRefundQuantity;

    @ApiModelProperty(value = "变更后退款数量")
    private Integer afterRefundQuantity;

    @ApiModelProperty(value = "银联支付订单号")
    private String unionpayOrderNo;

    @ApiModelProperty(value = "退款订单号")
    private String refundOrderId;

    @ApiModelProperty(value = "银联交易状态")
    private String unionpayStatus;

    @ApiModelProperty(value = "银联响应错误码")
    private String unionpayErrCode;

    @ApiModelProperty(value = "银联响应错误信息")
    private String unionpayErrMsg;

    @ApiModelProperty(value = "银联交易/回调时间原始值")
    private String unionpayTradeTime;

    @ApiModelProperty(value = "本次交易金额")
    private Integer tradeAmount;

    @ApiModelProperty(value = "本次业务处理是否成功")
    private Integer success;

    @ApiModelProperty(value = "备注")
    private String remark;

    @ApiModelProperty(value = "请求报文或回调原始参数")
    private String requestContent;

    @ApiModelProperty(value = "银联响应报文或本地处理结果")
    private String responseContent;

    @ApiModelProperty(value = "异常信息")
    private String exceptionMessage;

    @ApiModelProperty(value = "操作人ID")
    private Long operatorId;

    @ApiModelProperty(value = "操作人名称")
    private String operatorName;

    @ApiModelProperty(value = "客户端IP")
    private String clientIp;

    @ApiModelProperty(value = "链路追踪ID")
    private String traceId;

    @ApiModelProperty(value = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 日志类型：订单状态变化，例如创建订单、放弃支付。
     */
    public static final Integer LOG_TYPE_ORDER_STATUS = 1;
    /**
     * 日志类型：支付交易变化，例如支付查询、支付回调、银联关单。
     */
    public static final Integer LOG_TYPE_PAY = 2;
    /**
     * 日志类型：退款交易变化，例如退款申请、退款回调、退款查询。
     */
    public static final Integer LOG_TYPE_REFUND = 3;
    /**
     * 日志类型：核销操作。
     */
    public static final Integer LOG_TYPE_VERIFICATION = 4;
    /**
     * 日志类型：补偿任务，例如 Redis 超时补偿或异步查询补偿。
     */
    public static final Integer LOG_TYPE_COMPENSATE = 5;

    /**
     * 业务动作：创建订单。
     */
    public static final String ACTION_ORDER_CREATE = "ORDER_CREATE";
    /**
     * 业务动作：放弃支付订单。
     */
    public static final String ACTION_ORDER_ABANDON = "ORDER_ABANDON";
    /**
     * 业务动作：关闭银联未支付订单。
     */
    public static final String ACTION_PAY_CLOSE = "PAY_CLOSE";
    /**
     * 业务动作：处理银联支付成功回调。
     */
    public static final String ACTION_PAY_NOTIFY = "PAY_NOTIFY";
    /**
     * 业务动作：主动查询银联支付状态。
     */
    public static final String ACTION_PAY_QUERY = "PAY_QUERY";
    /**
     * 业务动作：待支付订单 Redis 过期补偿。
     */
    public static final String ACTION_ORDER_EXPIRE = "ORDER_EXPIRE";
    /**
     * 业务动作：申请退款。
     */
    public static final String ACTION_REFUND_APPLY = "REFUND_APPLY";
    /**
     * 业务动作：处理银联退款成功回调。
     */
    public static final String ACTION_REFUND_NOTIFY = "REFUND_NOTIFY";
    /**
     * 业务动作：主动查询银联退款状态。
     */
    public static final String ACTION_REFUND_QUERY = "REFUND_QUERY";
    /**
     * 业务动作：银联退款失败后回退本地退款中状态。
     */
    public static final String ACTION_REFUND_FAIL_ROLLBACK = "REFUND_FAIL_ROLLBACK";
    /**
     * 业务动作：订单核销。
     */
    public static final String ACTION_VERIFY_ORDER = "VERIFY_ORDER";

    /**
     * 事件来源：用户或后台管理员主动操作。
     */
    public static final String SOURCE_USER = "USER";
    /**
     * 事件来源：系统内部自动处理。
     */
    public static final String SOURCE_SYSTEM = "SYSTEM";
    /**
     * 事件来源：银联异步回调通知。
     */
    public static final String SOURCE_UNIONPAY_NOTIFY = "UNIONPAY_NOTIFY";
    /**
     * 事件来源：系统主动查询银联接口。
     */
    public static final String SOURCE_UNIONPAY_QUERY = "UNIONPAY_QUERY";
    /**
     * 事件来源：Redis 待支付订单过期事件。
     */
    public static final String SOURCE_REDIS_EXPIRE = "REDIS_EXPIRE";
    /**
     * 事件来源：Redis 退款查询补偿队列。
     */
    public static final String SOURCE_REDIS_REFUND_QUERY = "REDIS_REFUND_QUERY";
}
