package com.cui.edu.system.entity;

import java.math.BigDecimal;
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
 * 系统文件上传表
 * </p>
 *
 * @author Cuicui
 * @since 2026-05-28
 */
@Data
@EqualsAndHashCode(callSuper = false)
@ApiModel(value="SysFile对象", description="系统文件上传表")
public class SysFile implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "id")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "创建时间")
    private LocalDateTime createTime;

    @ApiModelProperty(value = "文件后缀名")
    private String suffixName;

    @ApiModelProperty(value = "文件大小（kb）")
    private BigDecimal size;

    @ApiModelProperty(value = "文件路径")
    private String path;

    @ApiModelProperty(value = "原文件名")
    private String formerName;

    @ApiModelProperty(value = "真实文件名（UUID+后缀名）")
    private String realName;

    @ApiModelProperty(value = "文件类型 1：图片 2：文档  3：视频 4：音频 5：其他")
    private Integer fileType;

    @ApiModelProperty(value = "本地存储-访问路径")
    private String requestPath;


    public static final String ID = "id";

    public static final String CREATE_TIME = "create_time";

    public static final String SUFFIX_NAME = "suffix_name";

    public static final String SIZE = "size";

    public static final String PATH = "path";

    public static final String FORMER_NAME = "former_name";

    public static final String REAL_NAME = "real_name";

    public static final String FILE_TYPE = "file_type";

    public static final String REQUEST_PATH = "request_path";

}
