package com.cui.edu.vo.trip;

import com.cui.edu.vo.BasisVO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(value = "团队查询参数")
public class TeamVO extends BasisVO {

    @ApiModelProperty(value = "团队名称")
    private String teamName;

    @ApiModelProperty(value = "负责人")
    private String principalName;

    @ApiModelProperty(value = "手机号")
    private String mobile;

    @ApiModelProperty(value = "微信openid")
    private String wechatOpenid;
}
