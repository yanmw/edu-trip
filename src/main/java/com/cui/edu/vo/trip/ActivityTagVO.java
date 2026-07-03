package com.cui.edu.vo.trip;

import com.cui.edu.vo.BasisVO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(value = "活动标签查询参数")
public class ActivityTagVO extends BasisVO {

    @ApiModelProperty(value = "标签名称")
    private String tagName;

    @ApiModelProperty(value = "状态：1启用 0禁用")
    private Integer status;

    @ApiModelProperty(value = "博物馆ID")
    private Long museumId;
}
