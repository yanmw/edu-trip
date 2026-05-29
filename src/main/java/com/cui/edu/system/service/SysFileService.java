package com.cui.edu.system.service;

import com.cui.edu.system.entity.SysFile;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

/**
 * <p>
 * 系统文件上传表 服务类
 * </p>
 *
 * @author Cuicui
 * @since 2026-05-28
 */
public interface SysFileService extends IService<SysFile> {

    /**
     * 上传文件到本地，并返回可访问地址。
     *
     * @param file 文件
     * @return 文件访问地址
     */
    String upload(MultipartFile file);

}
