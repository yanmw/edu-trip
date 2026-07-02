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

    /**
     * 根据微信小程序临时登录凭证 code 换取微信用户的唯一标识 OpenID
     * <p>
     * 业务流程：
     * 1. 校验本地配置是否已完整配置 appId 与 secret。
     * 2. 使用 UriComponentsBuilder 组装微信官方的 jscode2session 接口 URL。
     * 3. 发送 GET 请求，并解析 JSON 响应内容。
     * 4. 校验微信接口返回的 errcode：
     *    a. 若 errcode 存在且不为 0，说明 code 过期或配置有误，返回错误提示。
     *    b. 若返回成功，提取并验证 openid 是否非空。
     *    c. session_key 作为敏感会话密钥，仅在微信后台使用，这里过滤掉不返回给前端。
     *
     * @param code 微信小程序端调用 wx.login 获取的临时登录凭证
     * @return 包含 OpenID 的 WechatOpenidResultVO，若失败则包含错误信息
     */
    @Override
    public WechatOpenidResultVO getOpenid(String code) {
        // 1. 验证微信核心配置参数
        if (StringUtils.isBlank(appId) || StringUtils.isBlank(secret)) {
            return WechatOpenidResultVO.fail("微信小程序配置未完善");
        }

        // 2. 构造微信 auth.code2Session 接口的 HTTP 请求地址
        String url = UriComponentsBuilder.fromHttpUrl(JS_CODE_TO_SESSION_URL)
                .queryParam("appid", appId)
                .queryParam("secret", secret)
                .queryParam("js_code", code)
                .queryParam("grant_type", "authorization_code")
                .build()
                .toUriString();

        try {
            // 3. 调用微信接口换取用户会话信息
            String response = restTemplate.getForObject(url, String.class);
            JSONObject jsonObject = JSON.parseObject(response);
            
            // 4. 判断并处理微信接口的异常响应
            Integer errCode = jsonObject.getInteger("errcode");
            if (errCode != null && errCode != 0) {
                return WechatOpenidResultVO.fail(jsonObject.getString("errmsg"));
            }

            // 5. 校验并提取 OpenID
            String openid = jsonObject.getString("openid");
            if (StringUtils.isBlank(openid)) {
                return WechatOpenidResultVO.fail("微信未返回openid");
            }
            // session_key 不返回给前端，避免敏感会话信息外泄。
            return WechatOpenidResultVO.success(openid);
        } catch (RestClientException e) {
            // 6. 捕获网络通讯异常
            return WechatOpenidResultVO.fail("调用微信接口失败");
        }
    }
}
