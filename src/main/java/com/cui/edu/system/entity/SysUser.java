package com.cui.edu.system.entity;


import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 用户表
 * </p>
 *
 * @author Cuicui
 * @since 2026-02-25
 */
@Data
@EqualsAndHashCode(callSuper = false)
@ApiModel(value = "用户信息")
public class SysUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @ApiModelProperty(value = "id")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 创建人
     */
    @ApiModelProperty(value = "创建人")
    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    /**
     * 创建时间
     */
    @ApiModelProperty(value = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 更新人
     */
    @ApiModelProperty(value = "更新人")
    @TableField(fill = FieldFill.UPDATE)
    private String updateBy;

    /**
     * 更新时间
     */
    @ApiModelProperty(value = "更新时间")
    @TableField(fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /**
     * 用户名
     */
    @ApiModelProperty(value = "用户名")
    private String username;

    /**
     * 密码
     */
    @ApiModelProperty(value = "密码")
    private String password;

    /**
     * 加密盐
     */
    @ApiModelProperty(value = "加密盐")
    private String salt;

    /**
     * 手机号
     */
    @ApiModelProperty(value = "手机号")
    private String phone;

    /**
     * 微信openId
     */
    @ApiModelProperty(value = "微信openId")
    private String wxOpenId;

    /**
     * 博物馆id
     */
    @ApiModelProperty(value = "博物馆id")
    private String museumId;

    /**
     * 博物馆名称
     */
    @ApiModelProperty(value = "博物馆名称")
    @TableField(exist = false)
    private String museumName;

    /**
     * 状态  0：禁用   1：启用
     */
    @ApiModelProperty(value = "状态  0：禁用   1：启用")
    private Integer status;

    /**
     * 角色id列表
     */
    @ApiModelProperty(value = "角色id列表")
    @TableField(exist = false)
    private List<Long> roleIds;


    public static final String ID = "id";

    public static final String CREATE_BY = "create_by";

    public static final String CREATE_TIME = "create_time";

    public static final String UPDATE_BY = "update_by";

    public static final String UPDATE_TIME = "update_time";

    public static final String USERNAME = "username";

    public static final String PASSWORD = "password";

    public static final String SALT = "salt";

    public static final String PHONE = "phone";

    public static final String WX_OPEN_ID = "wx_open_id";

    public static final String MUSEUM_ID = "museum_id";

    public static final String STATUS = "status";

}
