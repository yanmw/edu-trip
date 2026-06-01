package com.cui.edu.vo.trip;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(description = "核销请求参数")
public class VerificationVO {
    @ApiModelProperty(value = "订单号")
    private String orderNo;
    @ApiModelProperty(value = "博物馆id")
    private String museumId;
}
