package com.cui.edu.trip.service.impl;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnionPayServiceImplTest {

    @Test
    void verifyNotifySignRequiresNotifySignKey() {
        UnionPayServiceImpl unionPayService = unionPayService("applet-key", "");
        Map<String, String[]> params = notifyParams("applet-key");

        assertFalse(unionPayService.verifyNotifySign(params));
    }

    @Test
    void verifyNotifySignUsesConfiguredNotifySignKey() {
        UnionPayServiceImpl unionPayService = unionPayService("applet-key", "notify-key");
        Map<String, String[]> params = notifyParams("notify-key");

        assertTrue(unionPayService.verifyNotifySign(params));
    }

    private UnionPayServiceImpl unionPayService(String appletAppKey, String notifySignKey) {
        UnionPayServiceImpl unionPayService = new UnionPayServiceImpl();
        ReflectionTestUtils.setField(unionPayService, "appletAppKey", appletAppKey);
        ReflectionTestUtils.setField(unionPayService, "notifySignKey", notifySignKey);
        return unionPayService;
    }

    private Map<String, String[]> notifyParams(String signKey) {
        Map<String, String[]> params = new HashMap<>();
        params.put("msgType", new String[]{"wx.notify"});
        params.put("merOrderId", new String[]{"TEST_ORDER_NO"});
        params.put("targetOrderId", new String[]{"4500000202202606167919234372"});
        params.put("totalAmount", new String[]{"300"});
        params.put("status", new String[]{"TRADE_SUCCESS"});
        params.put("signType", new String[]{"SHA256"});
        params.put("sign", new String[]{sha256Sign(params, signKey)});
        return params;
    }

    private String sha256Sign(Map<String, String[]> params, String signKey) {
        TreeMap<String, String> sortedParams = new TreeMap<>();
        for (Map.Entry<String, String[]> entry : params.entrySet()) {
            if ("sign".equals(entry.getKey())) {
                continue;
            }
            sortedParams.put(entry.getKey(), entry.getValue()[0]);
        }
        StringJoiner joiner = new StringJoiner("&");
        for (Map.Entry<String, String> entry : sortedParams.entrySet()) {
            joiner.add(entry.getKey() + "=" + entry.getValue());
        }
        return DigestUtils.sha256Hex((joiner + signKey).getBytes(StandardCharsets.UTF_8));
    }
}
