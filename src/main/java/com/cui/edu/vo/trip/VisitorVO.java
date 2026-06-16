package com.cui.edu.vo.trip;

import com.cui.edu.vo.BasisVO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(value = "游客查询参数")
public class VisitorVO extends BasisVO {

    @ApiModelProperty(value = "手机号")
    private String mobile;

    @ApiModelProperty(value = "身份证号")
    private String idCard;

    @ApiModelProperty(value = "姓名")
    private String name;

    @ApiModelProperty(value = "微信openid")
    private String wechatOpenid;

    @ApiModelProperty(value = "博物馆ID")
    private Long museumId;

    @ApiModelProperty(value = "省份")
    private String province;

    @ApiModelProperty(value = "城市")
    private String city;

    @ApiModelProperty(value = "性别（1：男；0：女；2：未知）")
    private Integer gender;

    @ApiModelProperty(value = "团队ID")
    private Long teamId;

    @ApiModelProperty(value = "游客批次号")
    private String batchNo;
}
