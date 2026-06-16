package com.cui.edu.vo.trip;

import com.cui.edu.vo.BasisVO;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
@ApiModel(value = "订单查询参数")
public class OrderVO extends BasisVO {

    @ApiModelProperty(value = "游客微信openId")
    private String openId;

    @ApiModelProperty(value = "订单号")
    private String orderNo;

    @ApiModelProperty(value = "博物馆ID")
    private Long museumId;

    @ApiModelProperty(value = "游客ID")
    private Long visitorId;

    @ApiModelProperty(value = "团队ID")
    private Long teamId;

    @ApiModelProperty(value = "订单类型（1：个人；2：团队）")
    private Integer orderType;

    @ApiModelProperty(value = "订单状态 1：支付中 10：支付成功；-1：放弃支付；-2：部分退款；-10：全额退款；-11：退款中")
    private Integer orderStatus;

    @ApiModelProperty(value = "是否使用：1已使用 0未使用")
    private Integer isUsed;

    @ApiModelProperty(value = "预约日期")
    @JsonFormat(pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate appointmentDate;
}
