package com.cui.edu.vo.trip;

import com.cui.edu.vo.BasisVO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(value = "活动管理查询参数")
public class ActivityManageVO extends BasisVO {

    @ApiModelProperty(value = "活动名称")
    private String activityName;

    @ApiModelProperty(value = "活动类型ID")
    private Long activityTypeId;

    @ApiModelProperty(value = "是否热门：1是 0否")
    private Integer isHot;

    @ApiModelProperty(value = "博物馆ID")
    private Long museumId;

    @ApiModelProperty(value = "状态：1启用 0禁用")
    private Integer status;
}
