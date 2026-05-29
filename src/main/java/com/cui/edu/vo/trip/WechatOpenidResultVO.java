package com.cui.edu.vo.trip;

import lombok.Data;

@Data
public class WechatOpenidResultVO {

    private boolean success;

    private String openid;

    private String message;

    public static WechatOpenidResultVO success(String openid) {
        WechatOpenidResultVO result = new WechatOpenidResultVO();
        result.setSuccess(true);
        result.setOpenid(openid);
        return result;
    }

    public static WechatOpenidResultVO fail(String message) {
        WechatOpenidResultVO result = new WechatOpenidResultVO();
        result.setSuccess(false);
        result.setMessage(message);
        return result;
    }
}
