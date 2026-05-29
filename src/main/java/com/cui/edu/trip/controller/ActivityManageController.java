package com.cui.edu.trip.controller;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.cui.edu.common.HttpResult;
import com.cui.edu.common.HttpStatus;
import com.cui.edu.common.PageResult;
import com.cui.edu.trip.entity.ActivityManage;
import com.cui.edu.trip.service.ActivityManageService;
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
            boolean saved = activityManageService.saveActivityManage(record);
            if (!saved) {
                // 活动单价是历史订单计价依据，修改价格需新建活动。
                return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "活动单价不允许修改，请禁用活动后，新建一个活动内容");
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
    public HttpResult findById(@ApiParam(value = "主键ID") @PathVariable Long id) {
        if (ObjectUtil.isNotEmpty(id)) {
            ActivityManage activityManage = activityManageService.findById(id);
            return HttpResult.ok(activityManage);
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    @GetMapping(value = "/findByMuseumId/{museumId}")
    @ApiOperation(value = "根据博物馆ID查询活动列表")
    public HttpResult findByMuseumId(@ApiParam(value = "博物馆ID") @PathVariable Long museumId) {
        if (ObjectUtil.isNotEmpty(museumId)) {
            List<ActivityManage> activityManageList = activityManageService.findByMuseumId(museumId);
            return HttpResult.ok(activityManageList);
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }
}
