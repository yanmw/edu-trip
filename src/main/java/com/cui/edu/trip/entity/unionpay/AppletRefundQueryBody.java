package com.cui.edu.trip.entity.unionpay;

import lombok.Data;

@Data
public class AppletRefundQueryBody {
    /**
     * 报文请求时间
     */
    private String requestTimestamp;
    /**
     * 商户订单号
     */
    private String merOrderId;
    /**
     * 商户号
     */
    private String mid;
    /**
     * 终端号
     */
    private String tid;
    /**
     * 业务类型
     */
    private String instMid = "MINIDEFAULT";
}
