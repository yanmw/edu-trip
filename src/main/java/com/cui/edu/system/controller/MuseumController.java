package com.cui.edu.system.controller;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.cui.edu.common.HttpResult;
import com.cui.edu.common.HttpStatus;
import com.cui.edu.common.PageResult;
import com.cui.edu.common.SysConstants;
import com.cui.edu.system.entity.Museum;
import com.cui.edu.system.service.MuseumSaveResult;
import com.cui.edu.system.service.MuseumService;
import com.cui.edu.util.Log;
import com.cui.edu.vo.system.MuseumVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 博物馆管理 前端控制器
 * </p>
 *
 * @author Cuicui
 * @since 2026-02-25
 */
@RestController
@RequestMapping("/system/museum")
@Api(tags = "博物馆管理")
public class MuseumController {

    @Autowired
    private MuseumService museumService;

    @PostMapping(value = "/save")
    @ApiOperation(value = "新增/修改博物馆")
    @Log(title = "新增/修改博物馆")
    public HttpResult save(@RequestBody Museum record) {
        if (BeanUtil.isNotEmpty(record)) {
            // 新增博物馆必须配置银联商户号，后续下单支付会按博物馆读取mid。
            if (record.getId() == null && (StringUtils.isBlank(record.getName()) || StringUtils.isBlank(record.getMid()))) {
                return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
            }
            MuseumSaveResult saveResult = museumService.saveMuseum(record);
            if (MuseumSaveResult.DUPLICATE_NAME.equals(saveResult)) {
                return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "博物馆名称已存在");
            }
            if (MuseumSaveResult.DUPLICATE_MID.equals(saveResult)) {
                // mid存在数据库唯一约束，重复时作为业务校验结果返回给前端。
                return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "mid已存在");
            }
            return HttpResult.ok(record.getId());
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    @PostMapping(value = "/disableFeature")
    @ApiOperation(value = "禁用博物馆")
    @Log(title = "禁用博物馆")
    public HttpResult delete(@RequestBody List<Long> records) {
        if (ObjectUtil.isNotEmpty(records)) {
            museumService.logicDelete(records);
            return HttpResult.ok();
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    @PostMapping(value = "/findPage")
    @ApiOperation(value = "博物馆查询-分页")
    public HttpResult findPage(@RequestBody MuseumVO vo) {
        if (BeanUtil.isNotEmpty(vo)) {
            PageResult pageResult = museumService.findPage(vo);
            return HttpResult.ok(pageResult);
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    @GetMapping(value = "/findAll")
    @ApiOperation(value = "查询全部博物馆")
    public HttpResult findAll() {
        QueryWrapper<Museum> ew = new QueryWrapper<>();
        ew.eq(Museum.STATUS, SysConstants.IS_TRUE);
        ew.orderByDesc(Museum.ID);
        List<Museum> museumList = museumService.list(ew);
        return HttpResult.ok(museumList);
    }
}
