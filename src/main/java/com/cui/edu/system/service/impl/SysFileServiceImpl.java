package com.cui.edu.system.service.impl;

import com.cui.edu.common.HttpStatus;
import com.cui.edu.config.exception.MyException;
import com.cui.edu.system.entity.SysFile;
import com.cui.edu.system.mapper.SysFileMapper;
import com.cui.edu.system.service.SysFileService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * <p>
 * 系统文件上传表 服务实现类
 * </p>
 *
 * @author Cuicui
 * @since 2026-05-28
 */
@Service
public class SysFileServiceImpl extends ServiceImpl<SysFileMapper, SysFile> implements SysFileService {

    private static final DateTimeFormatter DATE_PATH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final Set<String> IMAGE_SUFFIXES = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "ico", "tif", "tiff"
    ));

    private static final Set<String> DOCUMENT_SUFFIXES = new HashSet<>(Arrays.asList(
            "txt", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "csv", "md", "rtf", "json", "xml"
    ));

    private static final Set<String> VIDEO_SUFFIXES = new HashSet<>(Arrays.asList(
            "mp4", "avi", "mov", "wmv", "flv", "mkv", "webm", "m4v", "3gp"
    ));

    private static final Set<String> AUDIO_SUFFIXES = new HashSet<>(Arrays.asList(
            "mp3", "wav", "aac", "flac", "ogg", "m4a", "wma", "amr"
    ));

    @Value("${edu.file.upload-path:${user.dir}/upload}")
    private String uploadPath;

    @Value("${edu.file.domain:}")
    private String domain;

    @Override
    public String upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new MyException(HttpStatus.SC_BAD_REQUEST, "上传文件不能为空");
        }

        String formerName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename());
        if (formerName.contains("..")) {
            throw new MyException(HttpStatus.SC_BAD_REQUEST, "文件名不合法");
        }

        String suffixName = getSuffixName(formerName);
        String realName = UUID.randomUUID().toString().replace("-", "") + (StringUtils.hasText(suffixName) ? "." + suffixName : "");
        String datePath = LocalDate.now().format(DATE_PATH_FORMATTER);

        try {
            Path rootPath = Paths.get(uploadPath).toAbsolutePath().normalize();
            Path folderPath = rootPath.resolve(datePath).normalize();
            Files.createDirectories(folderPath);

            Path targetPath = folderPath.resolve(realName).normalize();
            if (!targetPath.startsWith(rootPath)) {
                throw new MyException(HttpStatus.SC_BAD_REQUEST, "文件路径不合法");
            }

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            SysFile sysFile = new SysFile();
            sysFile.setCreateTime(LocalDateTime.now());
            sysFile.setSuffixName(suffixName);
            sysFile.setSize(BigDecimal.valueOf(file.getSize()).divide(BigDecimal.valueOf(1024), 2, RoundingMode.HALF_UP));
            sysFile.setPath(targetPath.toString());
            sysFile.setFormerName(formerName);
            sysFile.setRealName(realName);
            sysFile.setFileType(getFileType(suffixName));
            super.save(sysFile);
            String requestPath = buildAccessPath(sysFile.getId());
            sysFile.setRequestPath(requestPath);
            super.updateById(sysFile);
            return requestPath;
        } catch (IOException e) {
            throw new MyException(HttpStatus.SC_SERVICE_UNAVAILABLE, "文件上传失败");
        }
    }

    private String buildAccessPath(Long id) {
        String base = normalizeDomain();
        return base + "/system/sys-file/access/" + id;
    }

    private String normalizeDomain() {
        if (!StringUtils.hasText(domain)) {
            return "";
        }
        String base = domain.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    private String getSuffixName(String fileName) {
        int index = fileName.lastIndexOf(".");
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase();
    }

    private Integer getFileType(String suffixName) {
        if (IMAGE_SUFFIXES.contains(suffixName)) {
            return 1;
        }
        if (DOCUMENT_SUFFIXES.contains(suffixName)) {
            return 2;
        }
        if (VIDEO_SUFFIXES.contains(suffixName)) {
            return 3;
        }
        if (AUDIO_SUFFIXES.contains(suffixName)) {
            return 4;
        }
        return 5;
    }

}
