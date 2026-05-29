package com.cui.edu.trip.entity.unionpay;

import lombok.Data;

@Data
public class AppletPayBody {
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
    /**
     * 支付总金额
     */
    private Integer totalAmount;
    /**
     * 交易类型
     */
    private String tradeType = "MINI";
    /**
     * 微信用户openId
     */
    private String subOpenId;
    /**
     * 支付宝用户userId
     */
    private String userId;
    /**
     * 子商户appId
     */
    private String subAppId;
    /**
     * 请求系统预留字段
     */
    private String srcReserve;
    /**
     * 请求成功后回调地址
     */
    private String notifyUrl;
}
