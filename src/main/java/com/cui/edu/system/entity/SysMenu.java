package com.cui.edu.system.entity;

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

/**
 * <p>
 * 菜单管理
 * </p>
 *
 * @author Cuicui
 * @since 2026-02-25
 */
@Data
@EqualsAndHashCode(callSuper = false)
@ApiModel(value = "菜单信息")
public class SysMenu implements Serializable {

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
    private LocalDateTime updateTime;

    /**
     * 菜单名称
     */
    @ApiModelProperty(value = "菜单名称")
    private String name;

    /**
     * 父级菜单id，顶级菜单为：0
     */
    @ApiModelProperty(value = "父级菜单id，顶级菜单为：0")
    private Long parentId;

    /**
     * 菜单url
     */
    @ApiModelProperty(value = "菜单url")
    private String url;

    /**
     * 授权，例如：sys:user:add
     */
    @ApiModelProperty(value = "授权，例如：sys:user:add")
    private String perms;

    /**
     * 类型 0：目录；1：菜单；2：按钮
     */
    @ApiModelProperty(value = "类型 0：目录；1：菜单；2：按钮")
    private Integer type;

    /**
     * 菜单图标
     */
    @ApiModelProperty(value = "菜单图标")
    private String icon;


    public static final String ID = "id";

    public static final String CREATE_BY = "create_by";

    public static final String CREATE_TIME = "create_time";

    public static final String UPDATE_BY = "update_by";

    public static final String UPDATE_TIME = "update_time";

    public static final String NAME = "name";

    public static final String PARENT_ID = "parent_id";

    public static final String URL = "url";

    public static final String PERMS = "perms";

    public static final String TYPE = "type";

    public static final String ICON = "icon";

}
