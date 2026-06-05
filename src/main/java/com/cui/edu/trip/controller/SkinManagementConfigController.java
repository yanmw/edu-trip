package com.cui.edu.trip.controller;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.cui.edu.common.HttpResult;
import com.cui.edu.common.HttpStatus;
import com.cui.edu.common.PageResult;
import com.cui.edu.common.SysConstants;
import com.cui.edu.trip.entity.SkinManagementConfig;
import com.cui.edu.trip.service.SkinManagementConfigService;
import com.cui.edu.vo.trip.SkinManagementConfigVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 皮肤管理表 前端控制器
 * </p>
 *
 * @author Cuicui
 * @since 2026-05-29
 */
@RestController
@RequestMapping("/trip/skin-management-config")
@Api(tags = "皮肤管理")
public class SkinManagementConfigController {

    @Autowired
    private SkinManagementConfigService skinManagementConfigService;

    @PostMapping(value = "/save")
    @ApiOperation(value = "新增/修改皮肤配置")
    public HttpResult save(@RequestBody SkinManagementConfig record) {
        if (BeanUtil.isNotEmpty(record)) {
            if (record.getId() == null) {
                if (record.getStatus() == null) {
                    record.setStatus(SysConstants.IS_TRUE);
                }
                if (record.getIsDeleted() == null) {
                    record.setIsDeleted(SysConstants.IS_FALSE);
                }
            }
            skinManagementConfigService.saveOrUpdate(record);
            return HttpResult.ok(record.getId());
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    @PostMapping(value = "/delete")
    @ApiOperation(value = "删除皮肤配置")
    public HttpResult delete(@RequestBody List<Long> records) {
        if (ObjectUtil.isNotEmpty(records)) {
            skinManagementConfigService.logicDelete(records);
            return HttpResult.ok();
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    @PostMapping(value = "/findPage")
    @ApiOperation(value = "皮肤配置查询-分页")
    public HttpResult findPage(@RequestBody SkinManagementConfigVO vo) {
        if (BeanUtil.isNotEmpty(vo)) {
            PageResult pageResult = skinManagementConfigService.findPage(vo);
            return HttpResult.ok(pageResult);
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    @GetMapping(value = "/findById/{id}")
    @ApiOperation(value = "根据ID查询皮肤配置详情")
    public HttpResult findById(@ApiParam(value = "主键ID") @PathVariable Long id) {
        if (ObjectUtil.isNotEmpty(id)) {
            SkinManagementConfig skinManagementConfig = skinManagementConfigService.findById(id);
            return HttpResult.ok(skinManagementConfig);
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    @GetMapping(value = "/findByMuseumId/{id}")
    @ApiOperation(value = "根据博物馆ID查询皮肤配置详情")
    public HttpResult findByMuseumId(@ApiParam(value = "博物馆ID") @PathVariable Long id) {
        if (ObjectUtil.isNotEmpty(id)) {
            SkinManagementConfig skinManagementConfig = skinManagementConfigService.findByMuseumId(id);
            return HttpResult.ok(skinManagementConfig);
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }
}
