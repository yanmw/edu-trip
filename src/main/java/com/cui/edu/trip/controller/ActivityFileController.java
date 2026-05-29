package com.cui.edu.trip.controller;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.cui.edu.common.HttpResult;
import com.cui.edu.common.HttpStatus;
import com.cui.edu.common.PageResult;
import com.cui.edu.common.SysConstants;
import com.cui.edu.trip.entity.ActivityFile;
import com.cui.edu.trip.service.ActivityFileService;
import com.cui.edu.vo.trip.ActivityFileVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 活动文件表 前端控制器
 * </p>
 *
 * @author Cuicui
 * @since 2026-05-29
 */
@RestController
@RequestMapping("/trip/activity-file")
@Api(tags = "活动文件管理")
public class ActivityFileController {

    @Autowired
    private ActivityFileService activityFileService;

    @PostMapping(value = "/save")
    @ApiOperation(value = "新增/修改活动文件")
    public HttpResult save(@RequestBody ActivityFile record) {
        if (BeanUtil.isNotEmpty(record)) {
            if (record.getId() == null && record.getIsDeleted() == null) {
                record.setIsDeleted(SysConstants.IS_FALSE);
            }
            activityFileService.saveOrUpdate(record);
            return HttpResult.ok(record.getId());
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    @PostMapping(value = "/delete")
    @ApiOperation(value = "删除活动文件")
    public HttpResult delete(@RequestBody List<Long> records) {
        if (ObjectUtil.isNotEmpty(records)) {
            activityFileService.logicDelete(records);
            return HttpResult.ok();
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    @PostMapping(value = "/findPage")
    @ApiOperation(value = "活动文件查询-分页")
    public HttpResult findPage(@RequestBody ActivityFileVO vo) {
        if (BeanUtil.isNotEmpty(vo)) {
            PageResult pageResult = activityFileService.findPage(vo);
            return HttpResult.ok(pageResult);
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }
}
