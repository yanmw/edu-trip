package com.cui.edu.trip.controller;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.cui.edu.common.HttpResult;
import com.cui.edu.common.HttpStatus;
import com.cui.edu.common.PageResult;
import com.cui.edu.trip.entity.Team;
import com.cui.edu.trip.service.TeamService;
import com.cui.edu.vo.trip.TeamVO;
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
 * 团队表 前端控制器
 * </p>
 *
 * @author Cuicui
 * @since 2026-05-29
 */
@RestController
@RequestMapping("/trip/team")
@Api(tags = "团队管理")
public class TeamController {

    @Autowired
    private TeamService teamService;

    @PostMapping(value = "/save")
    @ApiOperation(value = "新增/修改团队")
    public HttpResult save(@RequestBody Team record) {
        if (BeanUtil.isNotEmpty(record)) {
            boolean saved = teamService.saveTeam(record);
            if (!saved) {
                // 微信openid重复属于业务校验失败，直接返回400和提示信息。
                return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "微信openid已存在");
            }
            return HttpResult.ok(record.getId());
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    @PostMapping(value = "/delete")
    @ApiOperation(value = "删除团队")
    public HttpResult delete(@RequestBody List<Long> records) {
        if (ObjectUtil.isNotEmpty(records)) {
            teamService.logicDelete(records);
            return HttpResult.ok();
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    @PostMapping(value = "/findPage")
    @ApiOperation(value = "团队查询-分页")
    public HttpResult findPage(@RequestBody TeamVO vo) {
        if (BeanUtil.isNotEmpty(vo)) {
            PageResult pageResult = teamService.findPage(vo);
            return HttpResult.ok(pageResult);
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    @GetMapping(value = "/findByWechatOpenid/{wechatOpenid}")
    @ApiOperation(value = "根据微信openid查询团队详情")
    public HttpResult findByWechatOpenid(@ApiParam(value = "微信openid") @PathVariable String wechatOpenid) {
        if (ObjectUtil.isNotEmpty(wechatOpenid)) {
            Team team = teamService.findByWechatOpenid(wechatOpenid);
            return HttpResult.ok(team);
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }
}
