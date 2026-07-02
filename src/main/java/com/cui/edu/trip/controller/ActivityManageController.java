package com.cui.edu.trip.controller;


import cn.dev33.satoken.annotation.SaIgnore;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.cui.edu.common.HttpResult;
import com.cui.edu.common.HttpStatus;
import com.cui.edu.common.PageResult;
import com.cui.edu.trip.entity.ActivityManage;
import com.cui.edu.trip.service.ActivityManageService;
import com.cui.edu.util.Log;
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

    /**
     * 新增或修改活动
     * <p>
     * 业务流程：
     * 1. 验证入参 record 是否为空。
     * 2. 调用 ActivityManageService.saveActivityManage 保存/更新活动数据，若存在业务逻辑校验失败（例如时间交叉、参数冲突），返回对应报错信息。
     * 3. 保存成功则返回当前活动的主键 ID。
     *
     * @param record 活动管理实体
     * @return 包含当前活动 ID 或校验失败信息的 HttpResult 响应
     */
    @PostMapping(value = "/save")
    @ApiOperation(value = "新增/修改活动")
    @Log(title = "新增/修改活动")
    public HttpResult save(@RequestBody ActivityManage record) {
        // 1. 校验入参非空
        if (BeanUtil.isNotEmpty(record)) {
            // 2. 调用服务层保存逻辑
            String errorMsg = activityManageService.saveActivityManage(record);
            if (errorMsg != null) {
                // 返回具体的业务校验失败提示
                return HttpResult.error(HttpStatus.SC_BAD_REQUEST, errorMsg);
            }
            return HttpResult.ok(record.getId());
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    /**
     * 批量逻辑删除活动
     *
     * @param records 待删除的活动主键 ID 集合
     * @return 成功状态或参数错误提示的 HttpResult 响应
     */
    @PostMapping(value = "/delete")
    @ApiOperation(value = "删除活动")
    @Log(title = "删除活动")
    public HttpResult delete(@RequestBody List<Long> records) {
        // 1. 校验待删除集合非空
        if (ObjectUtil.isNotEmpty(records)) {
            // 2. 调用逻辑删除服务
            activityManageService.logicDelete(records);
            return HttpResult.ok();
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    /**
     * 禁用或启用活动（修改活动上下架/发布状态）
     *
     * @param vo 包含活动 ID 及状态值的参数对象
     * @return 状态更新结果的 HttpResult 响应
     */
    @PostMapping(value = "/updateStatus")
    @ApiOperation(value = "禁用/启用活动")
    @Log(title = "禁用/启用活动")
    public HttpResult updateStatus(@ApiParam(value = "活动状态参数") @RequestBody ActivityManageStatusVO vo) {
        // 1. 校验状态参数完整性
        if (ObjectUtil.isNotEmpty(vo)
                && ObjectUtil.isNotEmpty(vo.getId())
                && ObjectUtil.isNotEmpty(vo.getStatus())) {
            // 2. 调用更新状态业务逻辑
            String errorMsg = activityManageService.updateStatus(vo.getId(), vo.getStatus());
            if (errorMsg != null) {
                return HttpResult.error(HttpStatus.SC_BAD_REQUEST, errorMsg);
            }
            return HttpResult.ok();
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    /**
     * 分页查询活动列表（供管理端使用，支持复杂的活动名称、类型、博物馆过滤条件）
     *
     * @param vo 包含分页和活动过滤字段的 ActivityManageVO 对象
     * @return 包含分页数据 (PageResult) 的 HttpResult 响应
     */
    @PostMapping(value = "/findPage")
    @ApiOperation(value = "活动查询-分页")
    public HttpResult findPage(@RequestBody ActivityManageVO vo) {
        // 1. 验证条件非空
        if (BeanUtil.isNotEmpty(vo)) {
            // 2. 分页过滤查询
            PageResult pageResult = activityManageService.findPage(vo);
            return HttpResult.ok(pageResult);
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    /**
     * 根据活动主键 ID 获取活动详情
     * <p>
     * 该接口支持小程序免登录查询（SaIgnore 标注）。
     *
     * @param id 活动主键 ID
     * @return 活动详情实体
     */
    @GetMapping(value = "/findById/{id}")
    @ApiOperation(value = "根据ID查询活动详情")
    @SaIgnore
    public HttpResult findById(@ApiParam(value = "主键ID") @PathVariable Long id) {
        // 1. 校验 ID 非空
        if (ObjectUtil.isNotEmpty(id)) {
            // 2. 获取详情并返回
            ActivityManage activityManage = activityManageService.findById(id);
            return HttpResult.ok(activityManage);
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    /**
     * 根据博物馆 ID 查询可预约的活动列表
     * <p>
     * 支持多重可选过滤条件，如：参与形式 (个人/团队)、活动分类 ID 等。主要用于小程序首页活动列表渲染。
     *
     * @param museumId           博物馆 ID
     * @param participationType  可选：参与形式，1：团队，2：个人
     * @param activityTypeId     可选：活动类型主键 ID
     * @return 匹配的活动列表数据
     */
    @GetMapping(value = "/findByMuseumId")
    @ApiOperation(value = "根据博物馆ID查询活动列表")
    @SaIgnore
    public HttpResult findByMuseumId(@ApiParam(value = "博物馆ID") @RequestParam Long museumId,
                                     @ApiParam(value = "活动分类，1：团队；2：个人")
                                     @RequestParam(required = false) Integer participationType,
                                     @ApiParam(value = "活动类型ID")
                                     @RequestParam(required = false) Long activityTypeId) {
        // 1. 校验博物馆 ID 非空
        if (ObjectUtil.isNotEmpty(museumId)) {
            // 2. 查询对应的活动列表
            List<ActivityManage> activityManageList = activityManageService.findByMuseumId(museumId, participationType, activityTypeId);
            return HttpResult.ok(activityManageList);
        } else {
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }
}
