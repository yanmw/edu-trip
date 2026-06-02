package com.cui.edu.trip.entity.unionpay;

import lombok.Data;

@Data
public class AppletCloseBody {
    /**
     * 报文请求时间
     */
    private String requestTimestamp;
    /**
     * 商户订单号
     */
    private String merOrderId;
    /**
     * 业务类型
     */
    private String instMid = "MINIDEFAULT";
    /**
     * 商户号
     */
    private String mid;
    /**
     * 终端号
     */
    private String tid;
    /**
     * 请求系统预留字段
     */
    private String srcReserve;
}
