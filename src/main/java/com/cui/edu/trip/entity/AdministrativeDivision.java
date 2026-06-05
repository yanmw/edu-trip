package com.cui.edu.trip.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * <p>
 * 行政区划码表
 * </p>
 *
 * @author Cuicui
 * @since 2026-06-05
 */
@Data
@EqualsAndHashCode(callSuper = false)
@ApiModel(value = "AdministrativeDivision对象", description = "行政区划码表")
public class AdministrativeDivision implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "主键ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "行政区划代码")
    private String code;

    @ApiModelProperty(value = "行政区划名称")
    private String name;

    @ApiModelProperty(value = "省级代码")
    private String provinceCode;

    @ApiModelProperty(value = "市级代码")
    private String cityCode;

    @ApiModelProperty(value = "区县级代码")
    private String areaCode;

    @ApiModelProperty(value = "级别：1省 2市 3区县")
    private Integer level;

    public static final String ID = "id";

    public static final String CODE = "code";

    public static final String NAME = "name";

    public static final String PROVINCE_CODE = "province_code";

    public static final String CITY_CODE = "city_code";

    public static final String AREA_CODE = "area_code";

    public static final String LEVEL = "level";
}
