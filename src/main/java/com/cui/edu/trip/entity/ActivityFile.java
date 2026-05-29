package com.cui.edu.trip.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import java.io.Serializable;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>
 * 活动文件表
 * </p>
 *
 * @author Cuicui
 * @since 2026-05-29
 */
@Data
@EqualsAndHashCode(callSuper = false)
@ApiModel(value="ActivityFile对象", description="活动文件表")
public class ActivityFile implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "主键ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "活动ID")
    private Long activityId;

    @ApiModelProperty(value = "文件名称")
    private String fileName;

    @ApiModelProperty(value = "文件URL")
    private String fileUrl;

    @ApiModelProperty(value = "是否删除")
    private Integer isDeleted;

    @ApiModelProperty(value = "创建时间")
    private LocalDateTime createTime;

    @ApiModelProperty(value = "创建人")
    private Long createBy;

    @ApiModelProperty(value = "更新时间")
    private LocalDateTime updateTime;

    @ApiModelProperty(value = "更新人")
    private Long updateBy;


    public static final String ID = "id";

    public static final String ACTIVITY_ID = "activity_id";

    public static final String FILE_NAME = "file_name";

    public static final String FILE_URL = "file_url";

    public static final String IS_DELETED = "is_deleted";

    public static final String CREATE_TIME = "create_time";

    public static final String CREATE_BY = "create_by";

    public static final String UPDATE_TIME = "update_time";

    public static final String UPDATE_BY = "update_by";

}
