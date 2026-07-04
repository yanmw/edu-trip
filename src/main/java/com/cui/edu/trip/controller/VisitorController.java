package com.cui.edu.trip.controller;


import cn.dev33.satoken.annotation.SaIgnore;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.cui.edu.common.HttpResult;
import com.cui.edu.common.HttpStatus;
import com.cui.edu.common.PageResult;
import com.cui.edu.common.SysConstants;
import java.util.Map;
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

    /**
     * 新增或修改游客信息。
     * <p>
     * 处理流程：
     * <ol>
     *   <li>校验手机号格式（必填，1[3-9]开头11位）</li>
     *   <li>校验身份证格式（可选，传入时校验15/18位格式、出生日期、地址码及校验码）</li>
     *   <li>校验博物馆ID是否存在</li>
     *   <li>若 wechatOpenid 已绑定未删除记录，则复用其主键执行更新，而非新增</li>
     *   <li>根据身份证或手机号号段自动补全省市和性别</li>
     *   <li>若为新增且 wechatOpenid 曾被软删除，则恢复该记录</li>
     * </ol>
     * 校验失败时不抛全局异常，直接在响应体中返回错误信息，便于前端展示。
     * </p>
     * 注意：该接口跳过 Sa-Token 鉴权（@SaIgnore），供小程序端直接调用。
     */
    @PostMapping(value = "/save")
    @ApiOperation(value = "新增/修改游客")
    @SaIgnore
    @Log(title = "新增/修改游客")
    public HttpResult save(@RequestBody Visitor record) {
        // record 为空对象时直接拒绝，避免无意义的下游调用
        if (BeanUtil.isNotEmpty(record)) {
            Map<String, Object> result = visitorService.saveVisitor(record);
            if (result.containsKey(SysConstants.MSG)) {
                return HttpResult.error(result.get(SysConstants.MSG).toString());
            }
            // 保存成功后返回游客主键 ID，供前端后续使用
            return HttpResult.ok(record.getId());
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    /**
     * 通过 Excel 批量导入游客。
     * <p>
     * Excel 必须包含且仅包含“姓名、手机号、身份证号”三列（顺序不限）。
     * 所有数据行逐行校验，收集全部错误后统一返回前端（不中断）；校验全部通过才批量入库。
     * 校验失败时将每一行的错误原因（含行号）直接返回给前端，不抛全局异常。
     * 导入成功后返回实际入库的行数。
     * </p>
     * 注意：该接口跳过 Sa-Token 鉴权（@SaIgnore），供小程序端直接调用。
     */
    @PostMapping(value = "/importExcel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ApiOperation(value = "Excel导入游客")
    @SaIgnore
    @Log(title = "Excel导入游客")
    public HttpResult importExcel(@ApiParam(value = "Excel文件", required = true) @RequestParam("file") MultipartFile file,
                                  @ApiParam(value = "团队ID", required = true) @RequestParam("teamId") Long teamId,
                                  @ApiParam(value = "游客批次号") @RequestParam(value = "batchNo", required = false) String batchNo) {
        // 文件和团队ID均为必填，缺一不可
        if (file == null || file.isEmpty() || ObjectUtil.isEmpty(teamId)) {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
        Map<String, Object> result = visitorService.importExcel(file, teamId, batchNo);
        if (result.containsKey(SysConstants.MSG)) {
            return HttpResult.error(result.get(SysConstants.MSG).toString());
        }
        return HttpResult.ok(result.get("count"));
    }

    /**
     * 下载游客导入 Excel 模板。
     * <p>
     * 返回包含"姓名、手机号、身份证号"表头的空白 xlsx 文件，
     * 文件名经 RFC 5987 编码，兼容主流浏览器的中文文件名下载。
     * </p>
     * 注意：该接口跳过 Sa-Token 鉴权（@SaIgnore）。
     */
    @GetMapping(value = "/downloadTemplate")
    @ApiOperation(value = "下载游客导入模板")
    @SaIgnore
    public ResponseEntity<byte[]> downloadTemplate() {
        byte[] data = visitorService.getImportTemplate();
        // 对中文文件名做 RFC 5987 编码，避免部分浏览器下载时文件名乱码
        String fileName = encodeFileName("游客导入模板.xlsx");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .body(data);
    }

    /**
     * 批量逻辑删除游客（软删除，仅将 is_deleted 置为 1）。
     * <p>
     * 请求体为游客主键 ID 列表，列表不能为空。
     * </p>
     */
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

    /**
     * 分页查询未删除的游客列表。
     * <p>
     * 支持按姓名（模糊）、手机号（模糊）、身份证（模糊）、微信 openid（精确）、
     * 博物馆 ID、省份、城市、性别、团队 ID、批次号等条件组合过滤，
     * 结果按主键倒序排列。
     * </p>
     */
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

    /**
     * 根据团队 ID 查询该团队下未删除的游客列表。
     * <p>
     * batchNo（批次号）为可选参数，传入时在团队范围内进一步按批次过滤。
     * 结果按主键倒序排列。
     * </p>
     * 注意：该接口跳过 Sa-Token 鉴权（@SaIgnore），供小程序端直接调用。
     */
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

    /**
     * 根据微信 openid 查询未删除的游客详情。
     * <p>
     * 若同一 openid 存在多条记录（异常情况），取主键最大的一条。
     * 未找到时返回 null，前端需自行判断。
     * </p>
     * 注意：该接口跳过 Sa-Token 鉴权（@SaIgnore），供小程序端直接调用。
     */
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

    /**
     * 将文件名编码为 RFC 5987 格式，避免中文文件名在 HTTP 头中乱码。
     * URLEncoder 默认将空格编码为 "+"，需替换为 "%20" 以符合 RFC 规范。
     */
    private String encodeFileName(String fileName) {
        try {
            return URLEncoder.encode(fileName, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            // UTF-8 在所有 JVM 中均受支持，此处理论上不会触发
            return fileName;
        }
    }
}
