package com.cui.edu.vo.system;

import com.cui.edu.vo.BasisVO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(value = "角色查询参数")
public class RoleVO extends BasisVO {
    @ApiModelProperty(value = "角色名称")
    private String name;
}
