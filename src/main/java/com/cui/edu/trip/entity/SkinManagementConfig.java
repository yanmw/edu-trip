package com.cui.edu.trip.entity;



import com.baomidou.mybatisplus.annotation.FieldFill;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import java.io.Serializable;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>
 * 皮肤管理表
 * </p>
 *
 * @author Cuicui
 * @since 2026-05-29
 */
@Data
@EqualsAndHashCode(callSuper = false)
@ApiModel(value="SkinManagementConfig对象", description="皮肤管理表")
public class SkinManagementConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "主键ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @ApiModelProperty(value = "创建人")
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @ApiModelProperty(value = "更新时间")
    @TableField(fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    @ApiModelProperty(value = "更新人")
    @TableField(fill = FieldFill.UPDATE)
    private Long updateBy;

    @ApiModelProperty(value = "博物馆ID")
    private Long museumId;

    @ApiModelProperty(value = "博物馆名称")
    @TableField(exist = false)
    private String museumName;

    @ApiModelProperty(value = "高亮字体颜色")
    private String highlightFontColor;

    @ApiModelProperty(value = "普通字体颜色")
    private String normalFontColor;

    @ApiModelProperty(value = "不可点击字体颜色")
    private String disabledFontColor;

    @ApiModelProperty(value = "背景颜色")
    private String backgroundColor;

    @ApiModelProperty(value = "按钮颜色")
    private String buttonColor;

    @ApiModelProperty(value = "标题字体大小")
    private Integer titleFontSize;

    @ApiModelProperty(value = "背景图1")
    private String backgroundImage1;

    @ApiModelProperty(value = "背景图2")
    private String backgroundImage2;

    @ApiModelProperty(value = "背景图3")
    private String backgroundImage3;

    @ApiModelProperty(value = "背景图4")
    private String backgroundImage4;

    @ApiModelProperty(value = "背景图5")
    private String backgroundImage5;

    @ApiModelProperty(value = "背景图6")
    private String backgroundImage6;

    @ApiModelProperty(value = "背景图7")
    private String backgroundImage7;

    @ApiModelProperty(value = "背景图8")
    private String backgroundImage8;

    @ApiModelProperty(value = "背景图9")
    private String backgroundImage9;

    @ApiModelProperty(value = "背景图10")
    private String backgroundImage10;

    @ApiModelProperty(value = "背景图11")
    private String backgroundImage11;

    @ApiModelProperty(value = "背景图12")
    private String backgroundImage12;

    @ApiModelProperty(value = "状态：1启用 0禁用")
    private Integer status;

    @ApiModelProperty(value = "是否删除")
    private Integer isDeleted;


    public static final String ID = "id";

    public static final String CREATE_TIME = "create_time";

    public static final String CREATE_BY = "create_by";

    public static final String UPDATE_TIME = "update_time";

    public static final String UPDATE_BY = "update_by";

    public static final String MUSEUM_ID = "museum_id";

    public static final String HIGHLIGHT_FONT_COLOR = "highlight_font_color";

    public static final String NORMAL_FONT_COLOR = "normal_font_color";

    public static final String DISABLED_FONT_COLOR = "disabled_font_color";

    public static final String BACKGROUND_COLOR = "background_color";

    public static final String BUTTON_COLOR = "button_color";

    public static final String TITLE_FONT_SIZE = "title_font_size";

    public static final String BACKGROUND_IMAGE_1 = "background_image_1";

    public static final String BACKGROUND_IMAGE_2 = "background_image_2";

    public static final String BACKGROUND_IMAGE_3 = "background_image_3";

    public static final String BACKGROUND_IMAGE_4 = "background_image_4";

    public static final String BACKGROUND_IMAGE_5 = "background_image_5";

    public static final String BACKGROUND_IMAGE_6 = "background_image_6";

    public static final String BACKGROUND_IMAGE_7 = "background_image_7";

    public static final String BACKGROUND_IMAGE_8 = "background_image_8";

    public static final String BACKGROUND_IMAGE_9 = "background_image_9";

    public static final String BACKGROUND_IMAGE_10 = "background_image_10";

    public static final String BACKGROUND_IMAGE_11 = "background_image_11";

    public static final String BACKGROUND_IMAGE_12 = "background_image_12";

    public static final String STATUS = "status";

    public static final String IS_DELETED = "is_deleted";

}
