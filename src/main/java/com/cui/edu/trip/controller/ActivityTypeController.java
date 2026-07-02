package com.cui.edu.trip.controller;


import cn.dev33.satoken.annotation.SaIgnore;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.cui.edu.common.HttpResult;
import com.cui.edu.common.HttpStatus;
import com.cui.edu.common.PageResult;
import com.cui.edu.common.SysConstants;
import com.cui.edu.trip.entity.ActivityType;
import com.cui.edu.trip.service.ActivityTypeService;
import com.cui.edu.util.Log;
import com.cui.edu.vo.trip.ActivityTypeVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 活动类型表 前端控制器
 * </p>
 *
 * @author Cuicui
 * @since 2026-05-29
 */
@RestController
@RequestMapping("/trip/activity-type")
@Api(tags = "活动类型管理")
public class ActivityTypeController {

    @Autowired
    private ActivityTypeService activityTypeService;

    @PostMapping(value = "/save")
    @ApiOperation(value = "新增/修改活动类型")
    @Log(title = "新增/修改活动类型")
    public HttpResult save(@RequestBody ActivityType record) {
        if (BeanUtil.isNotEmpty(record)) {
            if (record.getId() == null) {
                if (record.getStatus() == null) {
                    record.setStatus(SysConstants.IS_TRUE);
                }
                if (record.getIsDeleted() == null) {
                    record.setIsDeleted(SysConstants.IS_FALSE);
                }
            }
            activityTypeService.saveOrUpdate(record);
            return HttpResult.ok(record.getId());
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    @PostMapping(value = "/delete")
    @ApiOperation(value = "删除活动类型")
    @Log(title = "删除活动类型")
    public HttpResult delete(@RequestBody List<Long> records) {
        if (ObjectUtil.isNotEmpty(records)) {
            activityTypeService.logicDelete(records);
            return HttpResult.ok();
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    @PostMapping(value = "/findPage")
    @ApiOperation(value = "活动类型查询-分页")
    public HttpResult findPage(@RequestBody ActivityTypeVO vo) {
        if (BeanUtil.isNotEmpty(vo)) {
            PageResult pageResult = activityTypeService.findPage(vo);
            return HttpResult.ok(pageResult);
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    @GetMapping(value = "/findAll")
    @ApiOperation(value = "查询全部活动类型")
    public HttpResult findAll() {
        List<ActivityType> activityTypeList = activityTypeService.findAll();
        return HttpResult.ok(activityTypeList);
    }

    @GetMapping(value = "/findAll/{museumId}")
    @ApiOperation(value = "查询全部活动类型")
    @SaIgnore
    public HttpResult findAllByMuseumId(@PathVariable Long museumId) {
        List<ActivityType> activityTypeList = activityTypeService.findAllByMuseumId(museumId);
        return HttpResult.ok(activityTypeList);
    }
}
