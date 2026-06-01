package com.cui.edu.vo.trip;

import com.cui.edu.vo.BasisVO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(value = "订单查询参数")
public class OrderVO extends BasisVO {

    @ApiModelProperty(value = "游客微信openId")
    private String openId;

    @ApiModelProperty(value = "团队ID")
    private Long teamId;
}
