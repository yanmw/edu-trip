package com.cui.edu.system.entity;




import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 异常捕获表
 * </p>
 *
 * @author Cuicui
 * @since 2026-02-25
 */
@Data
@EqualsAndHashCode(callSuper = false)
@ApiModel(value = "异常捕获信息")
public class SysException implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @ApiModelProperty(value = "id")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 创建时间
     */
    @ApiModelProperty(value = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 异常类型
     */
    @ApiModelProperty(value = "异常类型")
    private String exceptionType;

    /**
     * 异常信息
     */
    @ApiModelProperty(value = "异常信息")
    private String exceptionInfo;

    /**
     * 异常详情
     */
    @ApiModelProperty(value = "异常详情")
    private String exceptionDetail;

    /**
     * http状态值
     */
    @ApiModelProperty(value = "http状态值")
    private Integer httpStatusCode;


    public static final String ID = "id";

    public static final String CREATE_TIME = "create_time";

    public static final String EXCEPTION_TYPE = "exception_type";

    public static final String EXCEPTION_INFO = "exception_info";

    public static final String EXCEPTION_DETAIL = "exception_detail";

    public static final String HTTP_STATUS_CODE = "http_status_code";

}
