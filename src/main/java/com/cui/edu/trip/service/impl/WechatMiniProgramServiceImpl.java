package com.cui.edu.trip.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.cui.edu.trip.service.WechatMiniProgramService;
import com.cui.edu.vo.trip.WechatOpenidResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 微信小程序服务实现。
 */
@Service
public class WechatMiniProgramServiceImpl implements WechatMiniProgramService {

    private static final String JS_CODE_TO_SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session";

    @Value("${edu.wechat.mini-program.app-id:}")
    private String appId;

    @Value("${edu.wechat.mini-program.secret:}")
    private String secret;

    @Autowired
    private RestTemplate restTemplate;

    @Override
    public WechatOpenidResultVO getOpenid(String code) {
        if (StringUtils.isBlank(appId) || StringUtils.isBlank(secret)) {
            return WechatOpenidResultVO.fail("微信小程序配置未完善");
        }

        String url = UriComponentsBuilder.fromHttpUrl(JS_CODE_TO_SESSION_URL)
                .queryParam("appid", appId)
                .queryParam("secret", secret)
                .queryParam("js_code", code)
                .queryParam("grant_type", "authorization_code")
                .build()
                .toUriString();

        try {
            String response = restTemplate.getForObject(url, String.class);
            JSONObject jsonObject = JSON.parseObject(response);
            Integer errCode = jsonObject.getInteger("errcode");
            if (errCode != null && errCode != 0) {
                return WechatOpenidResultVO.fail(jsonObject.getString("errmsg"));
            }

            String openid = jsonObject.getString("openid");
            if (StringUtils.isBlank(openid)) {
                return WechatOpenidResultVO.fail("微信未返回openid");
            }
            // session_key 不返回给前端，避免敏感会话信息外泄。
            return WechatOpenidResultVO.success(openid);
        } catch (RestClientException e) {
            return WechatOpenidResultVO.fail("调用微信接口失败");
        }
    }
}
