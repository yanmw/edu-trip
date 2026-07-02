package com.cui.edu.trip.controller;


import cn.dev33.satoken.annotation.SaIgnore;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.cui.edu.common.HttpResult;
import com.cui.edu.common.HttpStatus;
import com.cui.edu.common.PageResult;
import com.cui.edu.trip.entity.Visitor;
import com.cui.edu.trip.service.VisitorService;
import com.cui.edu.util.Log;
import com.cui.edu.vo.trip.VisitorVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * <p>
 * 游客表 前端控制器
 * </p>
 *
 * @author Cuicui
 * @since 2026-05-29
 */
@RestController
@RequestMapping("/trip/visitor")
@Api(tags = "游客管理")
public class VisitorController {

    @Autowired
    private VisitorService visitorService;

    @PostMapping(value = "/save")
    @ApiOperation(value = "新增/修改游客")
    @SaIgnore
    @Log(title = "新增/修改游客")
    public HttpResult save(@RequestBody Visitor record) {
        if (BeanUtil.isNotEmpty(record)) {
            visitorService.saveVisitor(record);
            return HttpResult.ok(record.getId());
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    @PostMapping(value = "/importExcel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ApiOperation(value = "Excel导入游客")
    @SaIgnore
    @Log(title = "Excel导入游客")
    public HttpResult importExcel(@ApiParam(value = "Excel文件", required = true) @RequestParam("file") MultipartFile file,
                                  @ApiParam(value = "团队ID", required = true) @RequestParam("teamId") Long teamId,
                                  @ApiParam(value = "游客批次号") @RequestParam(value = "batchNo", required = false) String batchNo) {
        if (file == null || file.isEmpty() || ObjectUtil.isEmpty(teamId)) {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
        int count = visitorService.importExcel(file, teamId, batchNo);
        return HttpResult.ok(count);
    }

    @GetMapping(value = "/downloadTemplate")
    @ApiOperation(value = "下载游客导入模板")
    @SaIgnore
    public ResponseEntity<byte[]> downloadTemplate() {
        byte[] data = visitorService.getImportTemplate();
        String fileName = encodeFileName("游客导入模板.xlsx");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .body(data);
    }

    @PostMapping(value = "/delete")
    @ApiOperation(value = "删除游客")
    @Log(title = "删除游客")
    public HttpResult delete(@RequestBody List<Long> records) {
        if (ObjectUtil.isNotEmpty(records)) {
            visitorService.logicDelete(records);
            return HttpResult.ok();
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    @PostMapping(value = "/findPage")
    @ApiOperation(value = "游客查询-分页")
    public HttpResult findPage(@RequestBody VisitorVO vo) {
        if (BeanUtil.isNotEmpty(vo)) {
            PageResult pageResult = visitorService.findPage(vo);
            return HttpResult.ok(pageResult);
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    @GetMapping(value = "/findByTeamId")
    @ApiOperation(value = "根据团队ID查询游客列表")
    @SaIgnore
    public HttpResult findByTeamId(@ApiParam(value = "团队ID", required = true) @RequestParam("teamId") Long teamId,
                                   @ApiParam(value = "游客批次号") @RequestParam(value = "batchNo", required = false) String batchNo) {
        if (ObjectUtil.isNotEmpty(teamId)) {
            List<Visitor> visitorList = visitorService.findByTeamId(teamId, batchNo);
            return HttpResult.ok(visitorList);
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    @GetMapping(value = "/findByWechatOpenid/{wechatOpenid}")
    @ApiOperation(value = "根据微信openid查询游客详情")
    @SaIgnore
    public HttpResult findByWechatOpenid(@ApiParam(value = "微信openid") @PathVariable String wechatOpenid) {
        if (ObjectUtil.isNotEmpty(wechatOpenid)) {
            Visitor visitor = visitorService.findByWechatOpenid(wechatOpenid);
            return HttpResult.ok(visitor);
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    private String encodeFileName(String fileName) {
        try {
            return URLEncoder.encode(fileName, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            return fileName;
        }
    }
}
