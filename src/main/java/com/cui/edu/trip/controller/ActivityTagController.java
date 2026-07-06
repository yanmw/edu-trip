package com.cui.edu.trip.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.annotation.SaMode;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.cui.edu.common.HttpResult;
import com.cui.edu.common.HttpStatus;
import com.cui.edu.common.PageResult;
import com.cui.edu.common.SysConstants;
import com.cui.edu.trip.entity.ActivityTag;
import com.cui.edu.trip.service.ActivityTagService;
import com.cui.edu.util.Log;
import com.cui.edu.vo.trip.ActivityTagVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 活动标签表 前端控制器
 * </p>
 *
 * @author Cuicui
 * @since 2026-07-03
 */
@RestController
@RequestMapping("/trip/activity-tag")
@Api(tags = "活动标签管理")
public class ActivityTagController {

    @Autowired
    private ActivityTagService activityTagService;

    @PostMapping(value = "/save")
    @ApiOperation(value = "新增/修改活动标签")
    @SaCheckPermission(value = {"active:tag:add", "active:tag:edit"}, mode = SaMode.OR)
    @Log(title = "新增/修改活动标签")
    public HttpResult save(@RequestBody ActivityTag record) {
        if (BeanUtil.isNotEmpty(record)) {
            if (record.getId() == null) {
                if (record.getStatus() == null) {
                    record.setStatus(SysConstants.IS_TRUE);
                }
                if (record.getIsDeleted() == null) {
                    record.setIsDeleted(SysConstants.IS_FALSE);
                }
            }
            activityTagService.saveOrUpdate(record);
            return HttpResult.ok(record.getId());
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    @PostMapping(value = "/delete")
    @ApiOperation(value = "删除活动标签")
    @SaCheckPermission("active:tag:delete")
    @Log(title = "删除活动标签")
    public HttpResult delete(@RequestBody List<Long> records) {
        if (ObjectUtil.isNotEmpty(records)) {
            activityTagService.logicDelete(records);
            return HttpResult.ok();
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    @PostMapping(value = "/findPage")
    @ApiOperation(value = "活动标签查询-分页")
    @SaCheckPermission("active:tag:search")
    public HttpResult findPage(@RequestBody ActivityTagVO vo) {
        if (BeanUtil.isNotEmpty(vo)) {
            PageResult pageResult = activityTagService.findPage(vo);
            return HttpResult.ok(pageResult);
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    @GetMapping(value = "/findAll")
    @ApiOperation(value = "查询全部启用的活动标签")
    @SaCheckPermission("active:tag:search")
    public HttpResult findAll() {
        List<ActivityTag> activityTagList = activityTagService.findAll();
        return HttpResult.ok(activityTagList);
    }

    @GetMapping(value = "/findAll/{museumId}")
    @ApiOperation(value = "根据博物馆ID查询活动标签")
    @SaIgnore
    public HttpResult findAllByMuseumId(@PathVariable Long museumId) {
        List<ActivityTag> activityTagList = activityTagService.findAllByMuseumId(museumId);
        return HttpResult.ok(activityTagList);
    }
}
