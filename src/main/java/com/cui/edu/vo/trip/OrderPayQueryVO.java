package com.cui.edu.vo.trip;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(value = "订单支付结果查询参数")
public class OrderPayQueryVO {

    @ApiModelProperty(value = "订单号")
    private String orderNo;
}
