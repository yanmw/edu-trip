package com.cui.edu.trip.controller;


import cn.dev33.satoken.annotation.SaIgnore;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.cui.edu.common.HttpResult;
import com.cui.edu.common.HttpStatus;
import com.cui.edu.common.PageResult;
import com.cui.edu.trip.entity.ActivityManage;
import com.cui.edu.trip.service.ActivityManageService;
import com.cui.edu.vo.trip.ActivityManageStatusVO;
import com.cui.edu.vo.trip.ActivityManageVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 活动管理表 前端控制器
 * </p>
 *
 * @author Cuicui
 * @since 2026-05-29
 */
@RestController
@RequestMapping("/trip/activity-manage")
@Api(tags = "活动管理")
public class ActivityManageController {

    @Autowired
    private ActivityManageService activityManageService;

    @PostMapping(value = "/save")
    @ApiOperation(value = "新增/修改活动")
    public HttpResult save(@RequestBody ActivityManage record) {
        if (BeanUtil.isNotEmpty(record)) {
            String errorMsg = activityManageService.saveActivityManage(record);
            if (errorMsg != null) {
                return HttpResult.error(HttpStatus.SC_BAD_REQUEST, errorMsg);
            }
            return HttpResult.ok(record.getId());
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    @PostMapping(value = "/delete")
    @ApiOperation(value = "删除活动")
    public HttpResult delete(@RequestBody List<Long> records) {
        if (ObjectUtil.isNotEmpty(records)) {
            activityManageService.logicDelete(records);
            return HttpResult.ok();
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    @PostMapping(value = "/updateStatus")
    @ApiOperation(value = "禁用/启用活动")
    public HttpResult updateStatus(@ApiParam(value = "活动状态参数") @RequestBody ActivityManageStatusVO vo) {
        if (ObjectUtil.isNotEmpty(vo)
                && ObjectUtil.isNotEmpty(vo.getId())
                && ObjectUtil.isNotEmpty(vo.getStatus())) {
            String errorMsg = activityManageService.updateStatus(vo.getId(), vo.getStatus());
            if (errorMsg != null) {
                return HttpResult.error(HttpStatus.SC_BAD_REQUEST, errorMsg);
            }
            return HttpResult.ok();
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    @PostMapping(value = "/findPage")
    @ApiOperation(value = "活动查询-分页")
    public HttpResult findPage(@RequestBody ActivityManageVO vo) {
        if (BeanUtil.isNotEmpty(vo)) {
            PageResult pageResult = activityManageService.findPage(vo);
            return HttpResult.ok(pageResult);
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    @GetMapping(value = "/findById/{id}")
    @ApiOperation(value = "根据ID查询活动详情")
    @SaIgnore
    public HttpResult findById(@ApiParam(value = "主键ID") @PathVariable Long id) {
        if (ObjectUtil.isNotEmpty(id)) {
            ActivityManage activityManage = activityManageService.findById(id);
            return HttpResult.ok(activityManage);
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    @GetMapping(value = "/findByMuseumId")
    @ApiOperation(value = "根据博物馆ID查询活动列表")
    @SaIgnore
    public HttpResult findByMuseumId(@ApiParam(value = "博物馆ID") @RequestParam Long museumId,
                                     @ApiParam(value = "活动分类，1：团队；2：个人")
                                     @RequestParam(required = false) Integer participationType,
                                     @ApiParam(value = "活动类型ID")
                                     @RequestParam(required = false) Long activityTypeId) {
        if (ObjectUtil.isNotEmpty(museumId)) {
            List<ActivityManage> activityManageList = activityManageService.findByMuseumId(museumId, participationType, activityTypeId);
            return HttpResult.ok(activityManageList);
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }
}
