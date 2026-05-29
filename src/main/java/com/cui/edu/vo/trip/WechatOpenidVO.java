package com.cui.edu.vo.trip;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(value = "微信openid获取参数")
public class WechatOpenidVO {

    @ApiModelProperty(value = "小程序登录凭证code")
    private String code;
}
