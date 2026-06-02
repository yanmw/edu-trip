package com.cui.edu.trip.service;


import com.alibaba.fastjson2.JSONObject;

import java.util.Map;

public interface UnionPayService {

    String wechatAppletPay(String orderId, Integer money, String mid, String tid, String subOpenId, String srcReserve, String subAppId) throws Exception;

    String aliPayAppletPay(String orderId, Integer money, String mid, String tid, String userId, String srcReserve) throws Exception;

    JSONObject appletQuery(String orderId, String mid, String tid) throws Exception;

    JSONObject appletClose(String orderId, String mid, String tid) throws Exception;

    JSONObject appletRefund(String orderId, String targetOrderId, Integer money, String mid, String tid, String refundOrderId) throws Exception;

    JSONObject appletRefundQuery(String refundOrderId, String mid, String tid) throws Exception;

    boolean verifyNotifySign(Map<String, String[]> parameterMap);
}
