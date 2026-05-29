package com.cui.edu.vo.system;

import com.cui.edu.vo.BasisVO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(value = "博物馆查询参数")
public class MuseumVO extends BasisVO {

    @ApiModelProperty(value = "博物馆名称")
    private String name;

}
