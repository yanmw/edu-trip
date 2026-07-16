package com.cui.edu.vo.reconciliation;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;

@Data
@ApiModel(value = "新对账异常查询参数")
public class ReconciliationAbnormalQueryVO {

    @ApiModelProperty(value = "博物馆ID", required = true)
    private String museumId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @ApiModelProperty(value = "核对开始日期（包含），格式yyyy-MM-dd", example = "2026-01-01", required = true)
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @ApiModelProperty(value = "核对结束日期（包含），格式yyyy-MM-dd", example = "2026-01-18", required = true)
    private LocalDate endDate;
}
