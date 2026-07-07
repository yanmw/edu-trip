package com.cui.edu.vo.trip;

import com.cui.edu.vo.BasisVO;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
@ApiModel(value = "评价查询参数")
public class EvaluationVO extends BasisVO {

    @ApiModelProperty(value = "订单ID")
    private Long orderId;

    @ApiModelProperty(value = "活动名称（模糊查询）")
    private String activityName;

    @ApiModelProperty(value = "评分等级（总体评分，精确匹配）")
    private Integer overallScore;

    @ApiModelProperty(value = "提交时间-开始（yyyy-MM-dd，后端自动补 00:00:00）")
    @JsonFormat(pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate createTimeStart;

    @ApiModelProperty(value = "提交时间-结束（yyyy-MM-dd，后端自动补 23:59:59）")
    @JsonFormat(pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate createTimeEnd;
}
