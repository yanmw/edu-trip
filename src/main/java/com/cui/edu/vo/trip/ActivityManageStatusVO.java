package com.cui.edu.vo.trip;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(value = "活动状态更新参数")
public class ActivityManageStatusVO {

    @ApiModelProperty(value = "活动ID")
    private Long id;

    @ApiModelProperty(value = "状态：1启用 0禁用")
    private Integer status;
}
