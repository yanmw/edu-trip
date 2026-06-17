package com.cui.edu.vo.trip;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
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

    @ApiModelProperty(value = "游客批次号，团队下单必传，个人下单不允许传")
    private String batchNo;

    @ApiModelProperty(value = "预约日期")
    @JsonFormat(pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate appointmentDate;

    @ApiModelProperty(value = "下单详情")
    private List<AppointmentDetailVO> list;

    @Data
    public static class AppointmentDetailVO {
        @ApiModelProperty(value = "活动管理表id")
        private Long activityManageId;

        @ApiModelProperty(value = "活动场次表id")
        private Long activityScheduleId;

        @ApiModelProperty(value = "数量")
        private Integer num;

    }
}
