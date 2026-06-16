package com.cui.edu.trip.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.cui.edu.common.SysConstants;
import com.cui.edu.trip.entity.unionpay.AppletCloseBody;
import com.cui.edu.trip.entity.unionpay.AppletPayBody;
import com.cui.edu.trip.entity.unionpay.AppletQueryBody;
import com.cui.edu.trip.entity.unionpay.AppletRefundBody;
import com.cui.edu.trip.entity.unionpay.AppletRefundQueryBody;
import com.cui.edu.trip.service.UnionPayService;
import com.cui.edu.util.DateTimeUtils;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.UUID;

@Service
@Slf4j
public class UnionPayServiceImpl implements UnionPayService {

    @Value("${unionPay.appletAppId}")
    private String appletAppId;

    @Value("${unionPay.appletAppKey}")
    private String appletAppKey;

    @Value("${unionPay.notifySignKey:}")
    private String notifySignKey;

    @Value("${unionPay.notifyUrl}")
    private String notifyUrl;

    @Value("${unionPay.appletPayUrl:https://api-mop.chinaums.com/v1/netpay/wx/unified-order}")
    private String appletPayUrl;

    @Value("${unionPay.appletQueryUrl:https://api-mop.chinaums.com/v1/netpay/query}")
    private String appletQueryUrl;

    @Value("${unionPay.appletCloseUrl:https://api-mop.chinaums.com/v1/netpay/close}")
    private String appletCloseUrl;

    @Value("${unionPay.appletRefundUrl:https://api-mop.chinaums.com/v1/netpay/refund}")
    private String appletRefundUrl;

    @Value("${unionPay.appletRefundQueryUrl:https://api-mop.chinaums.com/v1/netpay/refund-query}")
    private String appletRefundQueryUrl;

    @Value("${unionPay.aliPayAppletPayUrl:https://api-mop.chinaums.com/v1/netpay/trade/create}")
    private String aliPayAppletPayUrl;

    // === 连接池配置 ===
    private static final int MAX_TOTAL_CONNECTIONS = 200;        // 最大总连接数
    private static final int MAX_PER_ROUTE = 50;                 // 每个目标主机最大连接数
    private static final int CONNECT_TIMEOUT = 5000;             // 连接建立超时（毫秒）
    private static final int CONNECTION_REQUEST_TIMEOUT = 3000;  // 从连接池获取连接超时
    private static final int SOCKET_TIMEOUT = 10000;             // 数据传输超时

    private static final PoolingHttpClientConnectionManager connManager;

    private static final CloseableHttpClient HTTP_CLIENT;

    static {
        connManager = new PoolingHttpClientConnectionManager();
        connManager.setMaxTotal(MAX_TOTAL_CONNECTIONS);
        connManager.setDefaultMaxPerRoute(MAX_PER_ROUTE);

        RequestConfig defaultConfig = RequestConfig.custom()
                .setConnectTimeout(CONNECT_TIMEOUT)
                .setConnectionRequestTimeout(CONNECTION_REQUEST_TIMEOUT)
                .setSocketTimeout(SOCKET_TIMEOUT)
                .build();
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
     * @return 银联退款响应
     */
    @Override
    public JSONObject appletRefund(String orderId, String targetOrderId, Integer money, String mid, String tid, String refundOrderId) throws Exception {
        AppletRefundBody body = new AppletRefundBody();
        body.setRequestTimestamp(DateTimeUtils.getLocalDateTimeString());
        body.setMerOrderId(orderId);
        body.setTargetOrderId(targetOrderId);
        body.setRefundAmount(money);
        body.setMid(mid);
        body.setTid(tid);
        body.setRefundOrderId(refundOrderId);
        String bodyStr = JSON.toJSONString(body);
        log.info("小程序退款请求报文json：{}", bodyStr);
        JSONObject jsonObject = postUnionPay(appletRefundUrl, bodyStr, "小程序退款");
        logUnionPayBusinessError("小程序退款", jsonObject);
        return jsonObject;
    }

    /**
     * @param refundOrderId 退款订单号
     * @param mid           商户号
     * @param tid           终端号
     * @return 银联退款查询响应
     */
    @Override
    public JSONObject appletRefundQuery(String refundOrderId, String mid, String tid) throws Exception {
        AppletRefundQueryBody body = new AppletRefundQueryBody();
        body.setRequestTimestamp(DateTimeUtils.getLocalDateTimeString());
        body.setMerOrderId(refundOrderId);
        body.setMid(mid);
        body.setTid(tid);
        String bodyStr = JSON.toJSONString(body);
        log.info("小程序退款查询请求报文json：{}", bodyStr);
        JSONObject jsonObject = postUnionPay(appletRefundQueryUrl, bodyStr, "小程序退款查询");
        logUnionPayBusinessError("小程序退款查询", jsonObject);
        return jsonObject;
    }

    /**
     * @param orderId 订单号
     * @param mid     商户号
     * @param tid     终端号
     * @return 银联支付查询响应
     */
    @Override
    public JSONObject appletQuery(String orderId, String mid, String tid) throws Exception {
        AppletQueryBody body = new AppletQueryBody();
        body.setRequestTimestamp(DateTimeUtils.getLocalDateTimeString());
        body.setMerOrderId(orderId);
        body.setMid(mid);
        body.setTid(tid);
        String bodyStr = JSON.toJSONString(body);
        log.info("小程序支付查询请求报文json：{}", bodyStr);
        JSONObject jsonObject = postUnionPay(appletQueryUrl, bodyStr, "小程序支付查询");
        logUnionPayBusinessError("小程序支付查询", jsonObject);
        return jsonObject;
    }

    /**
     * @param orderId 订单号
     * @param mid     商户号
     * @param tid     终端号
     * @return 银联关单响应
     */
    @Override
    public JSONObject appletClose(String orderId, String mid, String tid) throws Exception {
        AppletCloseBody body = new AppletCloseBody();
        body.setRequestTimestamp(DateTimeUtils.getLocalDateTimeString());
        body.setMerOrderId(orderId);
        body.setMid(mid);
        body.setTid(tid);
        String bodyStr = JSON.toJSONString(body);
        log.info("小程序订单关闭请求报文json：{}", bodyStr);
        JSONObject jsonObject = postUnionPay(appletCloseUrl, bodyStr, "小程序订单关闭");
        logUnionPayBusinessError("小程序订单关闭", jsonObject);
        return jsonObject;
    }

    /**
     * @param orderId    订单号
     * @param money      支付金额
     * @param mid        商户号
     * @param tid        终端号
     * @param subOpenId  微信用户openId
     * @param srcReserve 银联备用字段
     * @param subAppId   微信小程序appId
     * @return 微信小程序支付参数
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
        log.info("小程序支付请求报文json：{}", bodyStr);
        JSONObject jsonObject = postUnionPay(appletPayUrl, bodyStr, "小程序支付");
        ensureUnionPaySuccess("小程序支付", jsonObject);
        JSONObject miniPayRequest = jsonObject.getJSONObject("miniPayRequest");
        if (miniPayRequest == null || miniPayRequest.isEmpty()) {
            throw new RuntimeException("银联小程序支付下单失败，miniPayRequest为空");
        }
        return miniPayRequest.toString();
    }

    /**
     * @param orderId    订单号
     * @param money      支付金额
     * @param mid        商户号
     * @param tid        终端号
     * @param userId     支付宝userId
     * @param srcReserve 银联备用字段
     * @return 支付宝小程序支付订单号
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
        log.info("支付宝小程序支付请求报文json：{}", bodyStr);
        JSONObject jsonObject = postUnionPay(aliPayAppletPayUrl, bodyStr, "支付宝小程序支付");
        ensureUnionPaySuccess("支付宝小程序支付", jsonObject);
        String targetOrderId = jsonObject.getString("targetOrderId");
        if (targetOrderId == null || targetOrderId.trim().isEmpty()) {
            throw new RuntimeException("银联支付宝小程序支付下单失败，targetOrderId为空");
        }
        return targetOrderId;
    }

    /**
     * 校验银联支付/退款异步通知签名。
     *
     * <p>文档要求除 sign 外的表单字段按 ASCII 字典序拼接，并在末尾追加通讯密钥后计算签名。
     * signType 为空时银联默认使用 MD5；传 SHA256 时按 SHA256 计算。</p>
     *
     * @param parameterMap 回调表单参数
     * @return true 表示验签通过
     */
    @Override
    public boolean verifyNotifySign(Map<String, String[]> parameterMap) {
        String sign = getFirstValue(parameterMap, "sign");
        if (sign == null || sign.trim().isEmpty()) {
            log.warn("银联回调验签失败：sign为空");
            return false;
        }

        String waitSign = buildNotifyWaitSign(parameterMap) + getNotifySignKey();
        String signType = getFirstValue(parameterMap, "signType");
        String calculatedSign;
        if ("SHA256".equalsIgnoreCase(signType)) {
            calculatedSign = DigestUtils.sha256Hex(waitSign.getBytes(StandardCharsets.UTF_8));
        } else {
            calculatedSign = DigestUtils.md5Hex(waitSign.getBytes(StandardCharsets.UTF_8));
        }

        boolean passed = sign.equalsIgnoreCase(calculatedSign);
        if (!passed) {
            log.warn("银联回调验签失败：signType={}，waitSign={}", signType, buildNotifyWaitSign(parameterMap));
        }
        return passed;
    }

    private JSONObject postUnionPay(String url, String entity, String operationName) throws Exception {
        // 银联业务接口使用 OPEN-BODY-SIG：签名必须绑定本次请求体，避免拉起支付时认证失败。
        String send = send(url, entity, buildOpenBodySigAuthorization(entity));
        log.info("{}返回报文json：{}", operationName, send);
        return JSONObject.parseObject(send);
    }

    /**
     * 发送银联接口请求。
     */
    public String send(String url, String entity, String authorization) throws Exception {
        HttpPost post = new HttpPost(url);
        post.addHeader("Authorization", authorization);
        StringEntity requestEntity = new StringEntity(entity, StandardCharsets.UTF_8);
        requestEntity.setContentType("application/json");
        post.setEntity(requestEntity);

        try (CloseableHttpResponse response = HTTP_CLIENT.execute(post)) {
            int statusCode = response.getStatusLine().getStatusCode();
            HttpEntity responseEntity = response.getEntity();
            String responseText = responseEntity == null ? "" : EntityUtils.toString(responseEntity, StandardCharsets.UTF_8);
            if (statusCode < 200 || statusCode >= 300) {
                log.error("银联HTTP请求失败，url={}，statusCode={}，response={}", url, statusCode, responseText);
                throw new RuntimeException("银联HTTP请求失败：" + statusCode);
            }
            return responseText;
        }
    }

    /**
     * 构建银联开放平台业务请求的 Authorization 认证头。
     *
     * <p>银联小程序支付示例的 OPEN-BODY-SIG 规则：
     * 1. 对原始 JSON 请求体按 UTF-8 做 SHA256 摘要，得到 bodyDigest；
     * 2. 按 appId + timestamp + nonce + bodyDigest 拼接待签名串；
     * 3. 使用 appKey 做 HmacSHA256 签名并 Base64 编码；
     * 4. 将 AppId、Timestamp、Nonce、Signature 放入 Authorization。</p>
     */
    private String buildOpenBodySigAuthorization(String body) throws Exception {
        if (appletAppId == null || appletAppId.trim().isEmpty()
                || appletAppKey == null || appletAppKey.trim().isEmpty()) {
            throw new RuntimeException("银联OPEN-BODY-SIG认证参数缺失，请检查unionPay.appletAppId和unionPay.appletAppKey配置");
        }

        String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String requestBody = body == null ? "" : body;

        // bodyDigest 必须和实际发送的 JSON 字符串完全一致，否则银联侧会验签失败。
        String bodyDigest = DigestUtils.sha256Hex(requestBody.getBytes(StandardCharsets.UTF_8));
        String waitSign = appletAppId + timestamp + nonce + bodyDigest;
        String signature = hmacSha256Base64(waitSign, appletAppKey);

        log.info("银联OPEN-BODY-SIG认证参数：appId={}，timestamp={}，nonce={}，bodyDigest={}",
                appletAppId, timestamp, nonce, bodyDigest);
        return "OPEN-BODY-SIG AppId=\"" + appletAppId
                + "\", Timestamp=\"" + timestamp
                + "\", Nonce=\"" + nonce
                + "\", Signature=\"" + signature + "\"";
    }

    /**
     * 使用 HmacSHA256 对待签名串签名，并按银联示例要求转成 Base64 字符串。
     */
    private String hmacSha256Base64(String content, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signatureBytes = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signatureBytes);
    }

    private void ensureUnionPaySuccess(String operationName, JSONObject jsonObject) {
        if (jsonObject == null) {
            throw new RuntimeException(operationName + "失败，银联响应为空");
        }
        if (!SysConstants.SUCCESS.equals(jsonObject.getString("errCode"))) {
            throw new RuntimeException(operationName + "失败，" + buildUnionPayErrorMessage(jsonObject));
        }
    }

    private void logUnionPayBusinessError(String operationName, JSONObject jsonObject) {
        if (jsonObject != null && !SysConstants.SUCCESS.equals(jsonObject.getString("errCode"))) {
            log.warn("{}业务响应非成功：{}", operationName, buildUnionPayErrorMessage(jsonObject));
        }
    }

    private String buildUnionPayErrorMessage(JSONObject jsonObject) {
        String errCode = jsonObject.getString("errCode");
        String errMsg = jsonObject.getString("errMsg");
        return "errCode=" + errCode + "，errMsg=" + errMsg;
    }

    private String buildNotifyWaitSign(Map<String, String[]> parameterMap) {
        TreeMap<String, String> sortedParams = new TreeMap<>();
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            String key = entry.getKey();
            String value = getFirstNonEmptyValue(entry.getValue());
            if (key == null || "sign".equals(key) || value == null || value.trim().isEmpty()) {
                continue;
            }
            sortedParams.put(key, value);
        }

        StringJoiner joiner = new StringJoiner("&");
        for (Map.Entry<String, String> entry : sortedParams.entrySet()) {
            joiner.add(entry.getKey() + "=" + entry.getValue());
        }
        return joiner.toString();
    }

    private String getNotifySignKey() {
        if (notifySignKey != null && !notifySignKey.trim().isEmpty()) {
            return notifySignKey;
        }
        return appletAppKey;
    }

    private String getFirstValue(Map<String, String[]> parameterMap, String key) {
        return getFirstNonEmptyValue(parameterMap.get(key));
    }

    private String getFirstNonEmptyValue(String[] values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
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
