package com.cui.edu.trip.controller;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.cui.edu.common.HttpResult;
import com.cui.edu.common.HttpStatus;
import com.cui.edu.common.PageResult;
import com.cui.edu.trip.entity.Visitor;
import com.cui.edu.trip.service.VisitorService;
import com.cui.edu.vo.trip.VisitorVO;
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
    public HttpResult save(@RequestBody Visitor record) {
        if (BeanUtil.isNotEmpty(record)) {
            visitorService.saveVisitor(record);
            return HttpResult.ok(record.getId());
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    @PostMapping(value = "/delete")
    @ApiOperation(value = "删除游客")
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
}
