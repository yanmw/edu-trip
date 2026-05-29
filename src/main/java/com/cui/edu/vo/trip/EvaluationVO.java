package com.cui.edu.vo.trip;

import com.cui.edu.vo.BasisVO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(value = "评价查询参数")
public class EvaluationVO extends BasisVO {

    @ApiModelProperty(value = "订单ID")
    private Long orderId;
}
