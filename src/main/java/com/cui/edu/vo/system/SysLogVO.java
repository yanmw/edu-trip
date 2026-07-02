package com.cui.edu.vo.system;

import com.cui.edu.vo.BasisVO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(value = "系统日志查询参数")
public class SysLogVO extends BasisVO {

    @ApiModelProperty(value = "用户名")
    private String userName;

    @ApiModelProperty(value = "用户操作")
    private String operation;
}
