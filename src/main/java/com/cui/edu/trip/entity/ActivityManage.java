package com.cui.edu.trip.entity;


import com.baomidou.mybatisplus.annotation.FieldFill;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.io.Serializable;
import java.util.List;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>
 * 活动管理表
 * </p>
 *
 * @author Cuicui
 * @since 2026-05-29
 */
@Data
@EqualsAndHashCode(callSuper = false)
@ApiModel(value="ActivityManage对象", description="活动管理表")
public class ActivityManage implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "主键ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "活动名称")
    private String activityName;

    @ApiModelProperty(value = "活动类型ID")
    private Long activityTypeId;

    @ApiModelProperty(value = "是否热门：1是 0否")
    private Integer isHot;

    @ApiModelProperty(value = "价格，单位分")
    private Integer price;

    @ApiModelProperty(value = "博物馆ID")
    private Long museumId;

    @ApiModelProperty(value = "博物馆名称")
    @TableField(exist = false)
    private String museumName;

    @ApiModelProperty(value = "封面URL")
    private String coverUrl;

    @ApiModelProperty(value = "成图URL")
    private String imageUrl;

    @ApiModelProperty(value = "适用人群")
    private String applicablePeople;

    @ApiModelProperty(value = "活动地点")
    private String activityLocation;

    @ApiModelProperty(value = "活动开始日期")
    @JsonFormat(pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate activityStartDate;

    @ApiModelProperty(value = "活动结束日期")
    @JsonFormat(pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate activityEndDate;

    @ApiModelProperty(value = "报名须知")
    private String registrationNotice;

    @ApiModelProperty(value = "状态：1启用 0禁用")
    private Integer status;

    @ApiModelProperty(value = "是否删除")
    private Integer isDeleted;

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

    @ApiModelProperty(value = "联系方式")
    private String contactNumber;

    @ApiModelProperty(value = "活动分类，1：团队；2：个人")
    private Integer participationType;

    // 活动场次为一对多子数据，不映射到活动主表字段。
    @TableField(exist = false)
    @ApiModelProperty(value = "活动场次")
    private List<ActivitySchedule> activityScheduleList;


    public static final String ID = "id";

    public static final String ACTIVITY_NAME = "activity_name";

    public static final String ACTIVITY_TYPE_ID = "activity_type_id";

    public static final String IS_HOT = "is_hot";

    public static final String PRICE = "price";

    public static final String MUSEUM_ID = "museum_id";

    public static final String COVER_URL = "cover_url";

    public static final String IMAGE_URL = "image_url";

    public static final String APPLICABLE_PEOPLE = "applicable_people";

    public static final String ACTIVITY_LOCATION = "activity_location";

    public static final String ACTIVITY_START_DATE = "activity_start_date";

    public static final String ACTIVITY_END_DATE = "activity_end_date";

    public static final String REGISTRATION_NOTICE = "registration_notice";

    public static final String STATUS = "status";

    public static final String IS_DELETED = "is_deleted";

    public static final String CREATE_TIME = "create_time";

    public static final String CREATE_BY = "create_by";

    public static final String UPDATE_TIME = "update_time";

    public static final String UPDATE_BY = "update_by";

    public static final String CONTACT_PHONE = "contact_number";

    public static final String PARTICIPATION_TYPE = "participation_type";

}
