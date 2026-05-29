package com.cui.edu.vo.trip;

import com.cui.edu.vo.BasisVO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(value = "活动类型查询参数")
public class ActivityTypeVO extends BasisVO {

    @ApiModelProperty(value = "类型名称")
    private String typeName;

    @ApiModelProperty(value = "状态：1启用 0禁用")
    private Integer status;
}
