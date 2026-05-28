package com.cui.edu.vo.system;

import com.cui.edu.vo.BasisVO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(value = "部门查询参数")
public class DeptVO extends BasisVO {

    @ApiModelProperty(value = "部门名称")
    private String name;

}
