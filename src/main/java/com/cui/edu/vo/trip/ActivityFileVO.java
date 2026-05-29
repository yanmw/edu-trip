package com.cui.edu.vo.trip;

import com.cui.edu.vo.BasisVO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(value = "活动文件查询参数")
public class ActivityFileVO extends BasisVO {

    @ApiModelProperty(value = "活动ID")
    private Long activityId;

    @ApiModelProperty(value = "文件名称")
    private String fileName;
}
