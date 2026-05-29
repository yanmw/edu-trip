package com.cui.edu.trip.service.impl;


import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.cui.edu.trip.entity.unionpay.AppletQueryBody;
import com.cui.edu.trip.entity.unionpay.AppletRefundBody;
import com.cui.edu.trip.entity.unionpay.AppletRefundQueryBody;
import com.cui.edu.trip.entity.unionpay.AppletPayBody;
import com.cui.edu.trip.service.UnionPayService;
import com.cui.edu.util.DateTimeUtils;
import com.cui.edu.util.TextCodeGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

@Service
@Slf4j
public class UnionPayServiceImpl implements UnionPayService {

    @Autowired
    private TextCodeGenerator textCodeGenerator;

    /**
     * 微信小程序：银联支付的AppletAppId、AppletAppKey
     */
    private static final String AppletAppId = "8a81c1bf76674cf6017922771025035a";
    private static final String AppletAppKey = "c92bf75e2a434baaab5ea39ee1800b74";

    /**
     * 微信小程序：支付、支付查询、退款、退款查询的接口地址
     */
    private static final String AppletPayUrl = "https://api-mop.chinaums.com/v1/netpay/wx/unified-order";
    private static final String AppletQueryUrl = "https://api-mop.chinaums.com/v1/netpay/query";
    private static final String AppletRefundUrl = "https://api-mop.chinaums.com/v1/netpay/refund";
    private static final String AppletRefundQueryUrl = "https://api-mop.chinaums.com/v1/netpay/refund-query";

    @Value("${unionPay.notifyUrl}")
    private String notifyUrl;

    /**
     * 支付宝小程序：支付接口地址
     */
    private static final String aliPayAppletPayUrl = "https://api-mop.chinaums.com/v1/netpay/trade/create";

    // === 连接池配置 ===
    private static final int MAX_TOTAL_CONNECTIONS = 200;        // 最大总连接数
    private static final int MAX_PER_ROUTE = 50;                 // 每个目标主机最大连接数
    private static final int CONNECT_TIMEOUT = 5000;             // 连接建立超时（毫秒）
    private static final int CONNECTION_REQUEST_TIMEOUT = 3000;  // 从连接池获取连接超时
    private static final int SOCKET_TIMEOUT = 10000;             // 数据传输超时

    // === HttpClient 实例（全局复用） ===
    private static final PoolingHttpClientConnectionManager connManager;
    // 单例HTTP客户端（复用连接池）
    private static final CloseableHttpClient HTTP_CLIENT;

    static {
        // 1. 初始化连接池
        connManager = new PoolingHttpClientConnectionManager();
        connManager.setMaxTotal(MAX_TOTAL_CONNECTIONS);
        connManager.setDefaultMaxPerRoute(MAX_PER_ROUTE);

        // 可选：针对特定域名设置连接上限
        // HttpHost apiHost = new HttpHost("api-mop.chinaums.com", 443, "https");
        // connManager.setMaxPerRoute(new HttpRoute(apiHost), 100);

        // 2. 请求超时配置
        RequestConfig defaultConfig = RequestConfig.custom()
                .setConnectTimeout(CONNECT_TIMEOUT)
                .setConnectionRequestTimeout(CONNECTION_REQUEST_TIMEOUT)
                .setSocketTimeout(SOCKET_TIMEOUT)
                .build();
        // 3. 构建HttpClient
        HTTP_CLIENT = HttpClients.custom()
                .setConnectionManager(connManager)
                .setDefaultRequestConfig(defaultConfig)
                .build();
    }


    /**
     * @param orderId       订单号
     * @param targetOrderId 银联支付订单号
     * @param money         退款金额
     * @param mid           商户号
     * @param tid           终端号
     * @param refundOrderId 退款订单号
     * @return
     */
    @Override
    public JSONObject appletRefund(String orderId, String targetOrderId, Integer money, String mid, String tid, String refundOrderId) throws Exception {
        AppletRefundBody body = new AppletRefundBody();
        body.setRequestTimestamp(DateTimeUtils.getLocalDateTimeString());
        body.setMerOrderId(orderId);
        body.setTargetOrderId(targetOrderId);
        body.setRefundAmount(money);
        // 退款必须使用原订单所属博物馆的银联商户号和终端号。
        body.setMid(mid);
        body.setTid(tid);
        body.setRefundOrderId(refundOrderId);
        String bodyStr = JSON.toJSONString(body);
        log.info("小程序退款请求报文json：" + bodyStr);
        String authorization = getToken();
        try {
            String send = send(AppletRefundUrl, bodyStr, authorization);
            log.info("小程序退款返回报文json：" + send);
            JSONObject jsonObject = JSONObject.parseObject(send);
            return jsonObject;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * @param refundOrderId 退款订单号
     * @param mid           商户号
     * @param tid           终端号
     * @return
     */
    @Override
    public JSONObject appletRefundQuery(String refundOrderId, String mid, String tid) throws Exception {
        AppletRefundQueryBody body = new AppletRefundQueryBody();
        body.setRequestTimestamp(DateTimeUtils.getLocalDateTimeString());
        body.setMerOrderId(refundOrderId);
        body.setMid(mid);
        body.setTid(tid);
        String bodyStr = JSON.toJSONString(body);
        log.info("小程序退款查询请求报文json：" + bodyStr);
        String authorization = getToken();
        try {
            String send = send(AppletRefundQueryUrl, bodyStr, authorization);
            log.info("小程序退款查询返回报文json：" + send);
            JSONObject jsonObject = JSONObject.parseObject(send);
            return jsonObject;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * @param orderId 订单号
     * @param mid     商户号
     * @param tid     终端号
     * @return
     */
    @Override
    public JSONObject appletQuery(String orderId, String mid, String tid) throws Exception {
        AppletQueryBody body = new AppletQueryBody();
        body.setRequestTimestamp(DateTimeUtils.getLocalDateTimeString());
        body.setMerOrderId(orderId);
        body.setMid(mid);
        body.setTid(tid);
        String bodyStr = JSON.toJSONString(body);
        log.info("小程序支付查询请求报文json：" + bodyStr);
        String authorization = getToken();
        try {
            String send = send(AppletQueryUrl, bodyStr, authorization);
            log.info("小程序支付查询返回报文json：" + send);
            JSONObject jsonObject = JSONObject.parseObject(send);
            return jsonObject;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * @param orderId    订单号
     * @param money      支付金额
     * @param mid        商户号
     * @param tid        终端号
     * @param subOpenId  微信用户openId
     * @param srcReserve 银联备用字段-我们用来当备注使用
     * @return
     */
    @Override
    public String wechatAppletPay(String orderId, Integer money, String mid, String tid, String subOpenId, String srcReserve, String subAppId) throws Exception {
        AppletPayBody body = new AppletPayBody();
        body.setRequestTimestamp(DateTimeUtils.getLocalDateTimeString());
        body.setMerOrderId(orderId);
        body.setMid(mid);
        body.setTid(tid);
        body.setTotalAmount(money);
        body.setSubOpenId(subOpenId);
        body.setSubAppId(subAppId);
        body.setSrcReserve(srcReserve);
        body.setNotifyUrl(notifyUrl);
        String bodyStr = JSON.toJSONString(body);
        //请求报文
        log.info("小程序支付请求报文json：" + bodyStr);
        String authorization = getToken();
        try {
            String send = send(AppletPayUrl, bodyStr, authorization);
            log.info("小程序支付返回报文json：" + send);
            JSONObject jsonObject = JSONObject.parseObject(send);
            JSONObject miniPayRequest = jsonObject.getJSONObject("miniPayRequest");
            return miniPayRequest.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * @param orderId    订单号
     * @param money      支付金额
     * @param mid        商户号
     * @param tid        终端号
     * @param userId     支付宝userId
     * @param srcReserve 银联备用字段-我们用来当备注使用
     * @return
     */
    @Override
    public String aliPayAppletPay(String orderId, Integer money, String mid, String tid, String userId, String srcReserve) throws Exception {
        AppletPayBody body = new AppletPayBody();
        body.setRequestTimestamp(DateTimeUtils.getLocalDateTimeString());
        body.setMerOrderId(orderId);
        body.setMid(mid);
        body.setTid(tid);
        body.setTotalAmount(money);
        body.setUserId(userId);
        body.setSrcReserve(srcReserve);
        body.setNotifyUrl(notifyUrl);
        String bodyStr = JSON.toJSONString(body);
        //请求报文
        log.info("小程序支付请求报文json：" + bodyStr);
        String authorization = getToken();
        try {
            String send = send(aliPayAppletPayUrl, bodyStr, authorization);
            log.info("小程序支付返回报文json：" + send);
            JSONObject jsonObject = JSONObject.parseObject(send);
            String targetOrderId = jsonObject.getString("targetOrderId");
            return targetOrderId;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }





    /**
     * 发送请求
     *
     * @param
     * @return
     * @throws Exception
     */
    public String send(String url, String entity, String authorization) throws Exception {
        HttpPost post = new HttpPost(url);
        post.addHeader("Authorization", authorization);
        post.setEntity(new StringEntity(entity, "application/json", "UTF-8"));

        try (CloseableHttpResponse response = HTTP_CLIENT.execute(post)) {
            return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        }
    }

    public String getToken() {
        JSONObject jsonObject = new JSONObject();
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss");
        String timestamp = format.format(new Date());
        String nonce = textCodeGenerator.generate();
        String all = AppletAppId + timestamp + nonce + AppletAppKey;
        String signature = DigestUtils.sha256Hex(getMessageBytes(all));
        jsonObject.put("appId", AppletAppId);
        jsonObject.put("timestamp", timestamp);
        jsonObject.put("nonce", nonce);
        jsonObject.put("signature", signature);
        jsonObject.put("signMethod", "SHA256");
        log.info("getToken" + jsonObject.toString());
        JSONObject response = doPost("https://api-mop.chinaums.com/v1/token/access", jsonObject);
        String accessToken = response.get("accessToken").toString();
        return "OPEN-ACCESS-TOKEN AccessToken=" + accessToken + "";
    }

    public byte[] getMessageBytes(String message) {
        try {
            return message.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("签名过程中出现错误");
        }
    }

    private JSONObject doPost(String url, JSONObject json) {
        HttpPost post = new HttpPost(url);

        try {
            // 设置JSON请求体
            StringEntity entity = new StringEntity(json.toString(), StandardCharsets.UTF_8);
            entity.setContentType("application/json");
            post.setEntity(entity);

            // 执行请求并处理响应
            try (CloseableHttpResponse response = HTTP_CLIENT.execute(post)) {
                int statusCode = response.getStatusLine().getStatusCode();
                HttpEntity responseEntity = response.getEntity();

                if (statusCode >= 200 && statusCode < 300) {
                    String result = EntityUtils.toString(responseEntity, StandardCharsets.UTF_8);
                    return JSONObject.parseObject(result);
                } else {
                    String errorResponse = EntityUtils.toString(responseEntity, StandardCharsets.UTF_8);
                    log.error("HTTP请求失败: {} - {}", statusCode, errorResponse);
                    throw new RuntimeException("HTTP请求失败: " + statusCode + " - " + errorResponse);
                }
            }
        } catch (IOException e) {
            log.error("HTTP请求IO异常: {}", e.getMessage(), e);
            throw new RuntimeException("HTTP请求失败: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("HTTP请求处理异常: {}", e.getMessage(), e);
            throw new RuntimeException("HTTP请求处理失败: " + e.getMessage(), e);
        }
    }

    @PreDestroy
    public void destroy() {
        try {
            HTTP_CLIENT.close();
        } catch (IOException e) {
            log.error("关闭HttpClient失败", e);
        }
    }

}
