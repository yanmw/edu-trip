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
    @ApiModelProperty(value = "是否允许核销过期订单（预约日期已过当天），true 时允许核销，核销时间记录为预约日期当天 23:59:59")
    private Boolean allowExpired = false;
}
