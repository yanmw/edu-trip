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
 * 手机号归属地号段表
 * </p>
 *
 * @author Cuicui
 * @since 2026-06-05
 */
@Data
@EqualsAndHashCode(callSuper = false)
@ApiModel(value = "MobileNumberSegment对象", description = "手机号归属地号段表")
public class MobileNumberSegment implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "主键ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "手机号前7位号段")
    private String segment;

    @ApiModelProperty(value = "省份")
    private String province;

    @ApiModelProperty(value = "城市")
    private String city;

    @ApiModelProperty(value = "运营商")
    private String operator;

    public static final String ID = "id";

    public static final String SEGMENT = "segment";

    public static final String PROVINCE = "province";

    public static final String CITY = "city";

    public static final String OPERATOR = "operator";
}
