package com.cui.edu.system.controller;


import cn.dev33.satoken.annotation.SaIgnore;
import com.cui.edu.common.HttpResult;
import com.cui.edu.system.entity.SysFile;
import com.cui.edu.system.service.SysFileService;
import com.cui.edu.util.Log;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * <p>
 * 系统文件上传表 前端控制器
 * </p>
 *
 * @author Cuicui
 * @since 2026-05-28
 */
@RestController
@RequestMapping("/system/sys-file")
@Api(tags = "文件管理")
public class SysFileController {

    @Autowired
    private SysFileService sysFileService;

    @Value("${edu.file.upload-path:${user.dir}/upload}")
    private String uploadPath;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ApiOperation("上传文件")
    @Log(title = "上传文件")
    public HttpResult upload(@ApiParam(value = "文件", required = true) @RequestParam("file") MultipartFile file) {
        String requestPath = sysFileService.upload(file);
        return HttpResult.ok(requestPath);
    }

    @GetMapping(value = "/access/{id}")
    @ApiOperation("访问文件")
    @SaIgnore
    public ResponseEntity<Resource> access(@ApiParam(value = "文件id", required = true) @PathVariable Long id) throws IOException {
        SysFile sysFile = sysFileService.getById(id);
        if (sysFile == null || !StringUtils.hasText(sysFile.getPath())) {
            return ResponseEntity.notFound().build();
        }

        Path rootPath = Paths.get(uploadPath).toAbsolutePath().normalize();
        Path filePath = Paths.get(sysFile.getPath()).toAbsolutePath().normalize();
        if (!filePath.startsWith(rootPath)) {
            return ResponseEntity.notFound().build();
        }
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new UrlResource(filePath.toUri());
        String contentType = Files.probeContentType(filePath);
        if (!StringUtils.hasText(contentType)) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        String fileName = StringUtils.hasText(sysFile.getFormerName()) ? sysFile.getFormerName() : sysFile.getRealName();
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name()).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedFileName)
                .body(resource);
    }

}
