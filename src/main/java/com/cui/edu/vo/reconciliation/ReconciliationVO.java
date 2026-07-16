package com.cui.edu.vo.reconciliation;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(value = "对账参数")
public class ReconciliationVO {

    @ApiModelProperty(value = "博物馆id")
    private String museumId;

    @ApiModelProperty(value = "开始日期")
    private String startDate;

    @ApiModelProperty(value = "结束日期")
    private String endDate;

}
