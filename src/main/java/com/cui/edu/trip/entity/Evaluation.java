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
 * 评价表
 * </p>
 *
 * @author Cuicui
 * @since 2026-05-29
 */
@Data
@EqualsAndHashCode(callSuper = false)
@ApiModel(value="Evaluation对象", description="评价表")
public class Evaluation implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "主键ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "订单ID")
    private Long orderId;

    @ApiModelProperty(value = "喜欢环节")
    private String favoritePart;

    @ApiModelProperty(value = "改进建议")
    private String improvementSuggestion;

    @ApiModelProperty(value = "期待")
    private String expectation;

    @ApiModelProperty(value = "总体评价评分")
    private Integer overallScore;

    @ApiModelProperty(value = "安排合理性评分")
    private Integer arrangementScore;

    @ApiModelProperty(value = "吸引力评分")
    private Integer attractionScore;

    @ApiModelProperty(value = "知识和沉浸感评分")
    private Integer knowledgeImmersionScore;

    @ApiModelProperty(value = "是否删除")
    private Integer isDeleted;

    @ApiModelProperty(value = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @ApiModelProperty(value = "更新时间")
    @TableField(fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;


    public static final String ID = "id";

    public static final String ORDER_ID = "order_id";

    public static final String FAVORITE_PART = "favorite_part";

    public static final String IMPROVEMENT_SUGGESTION = "improvement_suggestion";

    public static final String EXPECTATION = "expectation";

    public static final String OVERALL_SCORE = "overall_score";

    public static final String ARRANGEMENT_SCORE = "arrangement_score";

    public static final String ATTRACTION_SCORE = "attraction_score";

    public static final String KNOWLEDGE_IMMERSION_SCORE = "knowledge_immersion_score";

    public static final String IS_DELETED = "is_deleted";

    public static final String CREATE_TIME = "create_time";

    public static final String UPDATE_TIME = "update_time";

}
