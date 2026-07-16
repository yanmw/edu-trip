package com.cui.edu.vo.reconciliation;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class ReconciliationAbnormalResult {

    /** 当前查询的博物馆ID。 */
    private String museumId;
    /** 核对开始日期（包含）。 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;
    /** 核对结束日期（包含）。 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;
    /** 金额单位，固定为CENT（分）。 */
    private String amountUnit = "CENT";
    /** 总账平衡结果。 */
    private Balance balance = new Balance();
    /** 原始数据是否全部且仅被处理一次。 */
    private SourceControl sourceControl = new SourceControl();
    /** 核对区间内有效核销账单，替代旧/billing接口。 */
    private Billing billing = new Billing();
    /** 按大类、具体异常类型组织的异常详情。 */
    private List<AbnormalGroup> groups = new ArrayList<>();

    @Data
    public static class Balance {
        /** 核对区间内有效核销子订单数量。 */
        private long systemVerificationQuantity;
        /** 核对区间内有效核销金额。 */
        private long systemVerificationAmount;
        /** 系统核对区间内退款成功子订单数量。 */
        private long systemRefundCount;
        /** 系统核对区间内退款成功金额。 */
        private long systemRefundAmount;
        /** 银联核对区间内消费流水数量。 */
        private long unionPayCount;
        /** 银联核对区间内消费流水金额。 */
        private long unionPayAmount;
        /** 银联核对区间内退款流水数量。 */
        private long unionRefundCount;
        /** 银联核对区间内退款金额。 */
        private long unionRefundAmount;
        /** 银联净额：消费金额减退款金额。 */
        private long unionNetAmount;
        /** 所有异常分类承担的调整金额合计。 */
        private long abnormalAdjustmentAmount;
        /** 调整后银联净额。 */
        private long adjustedUnionNetAmount;
        /** 系统有效核销金额减调整后银联净额。 */
        private long balanceDifference;
        /** 差额为0且无未归类、重复处理数据时为true。 */
        private boolean balanced;
    }

    @Data
    public static class SourceControl {
        /** 核对区间内读取到的系统有效核销子订单数量。 */
        private long systemVerificationDetailCount;
        /** 已进入对账计算的系统有效核销子订单数量。 */
        private long classifiedSystemVerificationDetailCount;
        /** 核对区间内读取到的系统退款成功子订单数量。 */
        private long systemRefundDetailCount;
        /** 已进入对账计算的系统退款成功子订单数量。 */
        private long classifiedSystemRefundDetailCount;
        /** 核对区间内读取到的银联消费流水数量。 */
        private long unionPayRowCount;
        /** 已进入对账计算的银联消费流水数量。 */
        private long classifiedUnionPayRowCount;
        /** 核对区间内读取到的银联退款流水数量。 */
        private long unionRefundRowCount;
        /** 已进入对账计算的银联退款流水数量。 */
        private long classifiedUnionRefundRowCount;
        /** 有账面差额但没有命中任何已知异常规则的订单数量。 */
        private long unclassifiedCount;
        /** 同一份原始数据被重复承担金额调整的次数，正常应为0。 */
        private long duplicateClassificationCount;
    }

    @Data
    public static class Billing {
        /** 有效核销子订单总数量。 */
        private long totalQuantity;
        /** 有效核销总金额，单位分。 */
        private long totalAmount;
        /** 按核销月份、活动和固定单价汇总的账单明细。 */
        private List<BillingDetail> details = new ArrayList<>();
    }

    @Data
    public static class BillingDetail {
        /** 实际核销月份，格式yyyy-MM。 */
        private String verificationMonth;
        /** 活动ID。 */
        private Long activityId;
        /** 活动名称。 */
        private String activityName;
        /** 活动固定单价，单位分。 */
        private long activityPrice;
        /** 有效核销子订单数量。 */
        private long quantity;
        /** 有效核销金额，单位分。 */
        private long amount;
    }

    @Data
    public static class AbnormalGroup {
        /** 异常大类编码，例如PAYMENT_VERIFICATION、REFUND。 */
        private String groupCode;
        /** 异常大类中文名称。 */
        private String groupName;
        /** 该大类下固定返回的异常分类。 */
        private List<AbnormalCategory> categories = new ArrayList<>();
    }

    @Data
    public static class AbnormalCategory {
        /** 具体异常编码，供前端稳定判断和筛选。 */
        private String anomalyCode;
        /** 具体异常中文名称。 */
        private String anomalyName;
        /** 当前分类中的异常明细数量。 */
        private long orderCount;
        /** 当前分类承担的有符号账面调整金额合计。 */
        private long adjustmentAmount;
        /** 当前分类的完整异常明细；无异常时返回空数组。 */
        private List<AbnormalDetail> details = new ArrayList<>();
    }

    @Data
    public static class AbnormalDetail {
        /** 系统unionpay_order_no/银联merchant_order_no。 */
        private String tradeNo;
        /** 便于前端直接展示的异常原因。 */
        private String explanation;
        /** 该异常明细承担的有符号调整金额。 */
        private long adjustmentAmount;
        /** 同订单的其他异常标签，仅用于说明，不重复承担余额调整。 */
        private List<String> relatedAnomalyCodes = new ArrayList<>();
        private SystemSnapshot system = new SystemSnapshot();
        private UnionSnapshot union = new UnionSnapshot();
        private RefundCheck refundCheck = new RefundCheck();
    }

    @Data
    public static class SystemSnapshot {
        /** 是否找到了对应的系统主订单。 */
        private boolean orderExists;
        /** 系统主订单数据库ID。 */
        private Long orderId;
        /** 系统业务订单号。 */
        private String orderNo;
        /** 订单所属博物馆ID。 */
        private Long museumId;
        /** 订单类型，1个人、2团队。 */
        private Integer orderType;
        /** 订单购买数量。 */
        private Integer orderQuantity;
        /** 系统主订单状态。 */
        private Integer orderStatus;
        /** 系统核销标记，1表示已核销。 */
        private Integer isUsed;
        /** 系统订单原始支付金额，单位分。 */
        private long payAmount;
        /** 主订单累计退款金额，单位分。 */
        private long orderRefundAmount;
        /** 主订单累计退款数量。 */
        private Integer refundQuantity;
        /** 团队订单对应的团队ID。 */
        private Long teamId;
        /** 游客批次号。 */
        private String batchNo;
        /** 个人订单对应的游客ID。 */
        private Long visitorId;
        /** 系统订单创建时间。 */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime createTime;
        /** 系统订单更新时间。 */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime updateTime;
        /** 系统支付成功时间；业务上可近似使用创建时间。 */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime paySuccessTime;
        /** 预约日期。 */
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate appointmentDate;
        /** 实际核销时间。 */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime verificationTime;
        /** 核对区间内仍有效的已核销子订单数量，不包含已退款子订单。 */
        private long validVerificationQuantity;
        /** 核对区间内仍有效的已核销金额，单位分。 */
        private long validVerificationAmount;
        /** 系统核对区间内退款成功子订单数量。 */
        private long periodRefundCount;
        /** 系统核对区间内退款成功金额，单位分。 */
        private long periodRefundAmount;
        /** 系统该订单全部历史退款成功金额，单位分。 */
        private long cumulativeRefundAmount;
        /** 系统核对区间内每一笔退款子订单明细。 */
        private List<SystemRefundDetail> refundDetails = new ArrayList<>();
        /** 系统订单的全部子订单，替代旧detail接口中的orderDetailList。 */
        private List<SystemOrderDetail> orderDetails = new ArrayList<>();
    }

    @Data
    public static class SystemOrderDetail {
        /** 子订单数据库ID。 */
        private Long orderDetailId;
        /** 所属主订单ID。 */
        private Long orderId;
        /** 子订单号。 */
        private String orderNo;
        /** 博物馆ID。 */
        private Long museumId;
        /** 活动ID。 */
        private Long activityId;
        /** 活动名称。 */
        private String activityName;
        /** 活动固定单价，单位分。 */
        private long activityPrice;
        /** 活动场次ID。 */
        private Long activityScheduleId;
        /** 子订单金额，单位分。 */
        private long orderAmount;
        /** 子订单退款金额，单位分。 */
        private long refundAmount;
        /** 子订单状态。 */
        private Integer orderStatus;
        /** 退款业务编号。 */
        private String refundId;
        /** 退款原因。 */
        private String refundReason;
        /** 退款时间。 */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime refundTime;
        /** 子订单创建时间。 */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime createTime;
        /** 子订单更新时间。 */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime updateTime;
    }

    @Data
    public static class SystemRefundDetail {
        /** 退款子订单数据库ID。 */
        private Long orderDetailId;
        /** 系统退款业务编号。 */
        private String refundId;
        /** 系统退款成功时间。 */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime refundTime;
        /** 该子订单退款金额，单位分。 */
        private long refundAmount;
    }

    @Data
    public static class UnionSnapshot {
        /** 银联核对区间内是否存在消费流水。 */
        private boolean paymentExists;
        /** 银联核对区间内消费流水数量，正常同交易号只有一条。 */
        private long payCount;
        /** 银联核对区间内消费流水金额合计，单位分。 */
        private long payAmount;
        /** 银联核对区间内消费流水明细。 */
        private List<UnionPaymentDetail> paymentDetails = new ArrayList<>();
        /** 不在核对区间内的消费流水。 */
        private long historicalPayCount;
        private long historicalPayAmount;
        private List<UnionPaymentDetail> historicalPaymentDetails = new ArrayList<>();
        /** 银联核对区间内退款流水数量，同交易号允许多条。 */
        private long refundCount;
        /** 银联核对区间内退款流水金额合计，单位分。 */
        private long refundAmount;
        /** 银联核对区间内每一笔退款流水明细。 */
        private List<UnionRefundDetail> refundDetails = new ArrayList<>();
        /** 不在核对区间内的退款汇总。 */
        private long historicalRefundCount;
        private long historicalRefundAmount;
        /** 不在核对区间内的退款流水明细。 */
        private List<UnionRefundDetail> historicalRefundDetails = new ArrayList<>();
    }

    @Data
    public static class UnionPaymentDetail {
        /** 银联导入流水数据库ID。 */
        private Long id;
        private String merchantName;
        private String mid;
        private String tid;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate settlementDate;
        /** 银联消费交易时间。 */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime transactionTime;
        private String cardNo;
        /** 银联消费金额，单位分。 */
        private long amount;
        /** 银联手续费，单位分，保留正负方向。 */
        private long handlingCharge;
        /** 银联净额，单位分，保留正负方向。 */
        private long netAmount;
        /** 银联参考号，用于识别具体流水及重复导入。 */
        private String referenceNumber;
        private String type;
        private String channel;
        private String merchantOrderNo;
        /** 银商订单号。 */
        private String unionOrderNo;
    }

    @Data
    public static class UnionRefundDetail {
        /** 银联导入流水数据库ID。 */
        private Long id;
        private String merchantName;
        private String mid;
        private String tid;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate settlementDate;
        /** 银联退款交易时间。 */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime transactionTime;
        private String cardNo;
        /** 银联退款金额，统一返回正数，单位分。 */
        private long refundAmount;
        /** 银联手续费，单位分，保留正负方向。 */
        private long handlingCharge;
        /** 银联净额，单位分，保留正负方向。 */
        private long netAmount;
        /** 银联参考号，用于区分同订单的多笔退款。 */
        private String referenceNumber;
        private String type;
        private String channel;
        private String merchantOrderNo;
        /** 银商订单号。 */
        private String unionOrderNo;
    }

    @Data
    public static class RefundCheck {
        /** 系统核对区间内退款成功金额合计，单位分。 */
        private long systemRefundAmount;
        /** 银联核对区间内退款流水金额合计，单位分。 */
        private long unionRefundAmount;
        /** 退款差额：系统退款金额减银联退款金额。 */
        private long differenceAmount;
        /** 两边核对区间内退款总额是否一致。 */
        private boolean matched;
    }
}
