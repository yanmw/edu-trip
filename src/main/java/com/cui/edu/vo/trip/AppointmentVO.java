package com.cui.edu.vo.trip;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

@Data
@ApiModel(value = "下单参数")
public class AppointmentVO {

    @ApiModelProperty(value = "消费金额 单位分")
    private Integer money;

    @ApiModelProperty(value = "小程序openId （小程序支付时需要用的）")
    private String openId;

    @ApiModelProperty(value = "博物馆id")
    private Long museumId;

    @ApiModelProperty(value = "游客id")
    private Long visitorId;

    @ApiModelProperty(value = "团队id")
    private Long teamId;

    @ApiModelProperty(value = "下单详情")
    private List<AppointmentDetailVO> list;

    @Data
    public static class AppointmentDetailVO {
        @ApiModelProperty(value = "活动管理表id")
        private Long activityManageId;

        @ApiModelProperty(value = "数量")
        private Integer num;

    }
}
