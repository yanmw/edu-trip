package com.cui.edu.trip.controller;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.cui.edu.common.HttpResult;
import com.cui.edu.common.HttpStatus;
import com.cui.edu.trip.service.WechatMiniProgramService;
import com.cui.edu.vo.trip.WechatOpenidResultVO;
import com.cui.edu.vo.trip.WechatOpenidVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 微信小程序接口。
 */
@RestController
@RequestMapping("/trip/wechat-mini-program")
@Api(tags = "微信小程序")
public class WechatMiniProgramController {

    @Autowired
    private WechatMiniProgramService wechatMiniProgramService;

    @PostMapping(value = "/getOpenid")
    @ApiOperation(value = "获取微信小程序openid")
    public HttpResult getOpenid(@RequestBody WechatOpenidVO vo) {
        if (BeanUtil.isEmpty(vo) || StringUtils.isBlank(vo.getCode())) {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }

        WechatOpenidResultVO result = wechatMiniProgramService.getOpenid(vo.getCode());
        if (!result.isSuccess()) {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, result.getMessage());
        }
        return HttpResult.ok(result.getOpenid());
    }
}
