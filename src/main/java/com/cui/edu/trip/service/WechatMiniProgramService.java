package com.cui.edu.trip.service;

import com.cui.edu.vo.trip.WechatOpenidResultVO;

/**
 * 微信小程序服务。
 */
public interface WechatMiniProgramService {

    /**
     * 根据小程序登录code换取openid。
     */
    WechatOpenidResultVO getOpenid(String code);
}
