package com.cui.edu.trip.entity;




import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;
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
 * 游客表
 * </p>
 *
 * @author Cuicui
 * @since 2026-05-29
 */
@Data
@EqualsAndHashCode(callSuper = false)
@ApiModel(value="Visitor对象", description="游客表")
public class Visitor implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "主键ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "手机号")
    private String mobile;

    @ApiModelProperty(value = "身份证号")
    private String idCard;

    @ApiModelProperty(value = "姓名")
    private String name;

    @ApiModelProperty(value = "微信openid")
    private String wechatOpenid;

    @ApiModelProperty(value = "省份")
    private String province;

    @ApiModelProperty(value = "男女（1：男；0：女）")
    private Integer gender;

    @ApiModelProperty(value = "团队ID")
    private Long teamId;

    @ApiModelProperty(value = "是否删除")
    private Integer isDeleted;

    @ApiModelProperty(value = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;


    public static final String ID = "id";

    public static final String MOBILE = "mobile";

    public static final String ID_CARD = "id_card";

    public static final String NAME = "name";

    public static final String WECHAT_OPENID = "wechat_openid";

    public static final String PROVINCE = "province";

    public static final String GENDER = "gender";

    public static final String TEAM_ID = "team_id";

    public static final String IS_DELETED = "is_deleted";

    public static final String CREATE_TIME = "create_time";

}
