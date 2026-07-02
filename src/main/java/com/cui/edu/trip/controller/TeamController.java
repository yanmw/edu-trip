package com.cui.edu.trip.controller;


import cn.dev33.satoken.annotation.SaIgnore;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.cui.edu.common.HttpResult;
import com.cui.edu.common.HttpStatus;
import com.cui.edu.common.PageResult;
import com.cui.edu.trip.entity.Team;
import com.cui.edu.trip.service.TeamService;
import com.cui.edu.util.Log;
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

    /**
     * 新增或修改团队信息
     * <p>
     * 业务流程：
     * 1. 验证请求实体 record 是否为空。
     * 2. 调用 TeamService.saveTeam 执行新增/修改逻辑。
     * 3. 校验失败（通常为微信 OpenID 已存在，或由于并发导致数据库唯一索引冲突）时，返回 400 Bad Request 错误。
     * 4. 保存成功则返回当前团队的主键 ID。
     *
     * @param record 团队实体信息
     * @return 包含团队ID或错误信息的 HttpResult 响应
     */
    @PostMapping(value = "/save")
    @ApiOperation(value = "新增/修改团队")
    @SaIgnore
    @Log(title = "新增/修改团队")
    public HttpResult save(@RequestBody Team record) {
        // 1. 验证参数非空
        if (BeanUtil.isNotEmpty(record)) {
            // 2. 执行团队保存/更新业务逻辑
            boolean saved = teamService.saveTeam(record);
            if (!saved) {
                // 3. 业务校验失败：微信 openid 已存在（可能处于激活状态，或并发唯一键冲突），直接返回400和提示信息
                return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "微信openid已存在");
            }
            // 4. 保存成功，返回当前团队的 ID
            return HttpResult.ok(record.getId());
        } else {
            // 参数为空，返回参数有误提示
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    /**
     * 批量逻辑删除团队
     * <p>
     * 业务流程：
     * 1. 验证待删除团队主键列表 records 是否为空。
     * 2. 调用 TeamService.logicDelete 将 records 列表对应的团队 is_deleted 状态置为 1（逻辑删除）。
     *
     * @param records 待删除的团队主键 ID 集合
     * @return 成功或参数错误提示的 HttpResult 响应
     */
    @PostMapping(value = "/delete")
    @ApiOperation(value = "删除团队")
    @SaIgnore
    @Log(title = "删除团队")
    public HttpResult delete(@RequestBody List<Long> records) {
        // 1. 校验待删除ID集合非空
        if (ObjectUtil.isNotEmpty(records)) {
            // 2. 调用逻辑删除服务
            teamService.logicDelete(records);
            return HttpResult.ok();
        } else {
            // 参数为空，返回参数有误提示
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    /**
     * 分页查询团队列表
     * <p>
     * 业务流程：
     * 1. 验证分页查询条件 vo 是否为空。
     * 2. 调用 TeamService.findPage，根据团队名称、负责人姓名、电话、微信 OpenID 等条件过滤并分页。
     *
     * @param vo 包含分页信息及过滤条件的 TeamVO 对象
     * @return 包含分页数据 (PageResult) 的 HttpResult 响应
     */
    @PostMapping(value = "/findPage")
    @ApiOperation(value = "团队查询-分页")
    public HttpResult findPage(@RequestBody TeamVO vo) {
        // 1. 校验查询条件非空
        if (BeanUtil.isNotEmpty(vo)) {
            // 2. 执行分页查询
            PageResult pageResult = teamService.findPage(vo);
            return HttpResult.ok(pageResult);
        } else {
            // 参数为空，返回参数有误提示
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }

    /**
     * 根据微信 OpenID 查询处于未删除（激活）状态的团队详情
     *
     * @param wechatOpenid 微信用户唯一标识 OpenID
     * @return 包含团队实体信息的 HttpResult 响应，若未找到则返回空数据
     */
    @GetMapping(value = "/findByWechatOpenid/{wechatOpenid}")
    @ApiOperation(value = "根据微信openid查询团队详情")
    @SaIgnore
    public HttpResult findByWechatOpenid(@ApiParam(value = "微信openid") @PathVariable String wechatOpenid) {
        // 1. 校验微信 OpenID 参数非空
        if (ObjectUtil.isNotEmpty(wechatOpenid)) {
            // 2. 查询活跃状态的团队详情
            Team team = teamService.findByWechatOpenid(wechatOpenid);
            return HttpResult.ok(team);
        } else {
            // 参数为空，返回参数有误提示
            return HttpResult.error(HttpStatus.SC_BAD_REQUEST, "参数有误");
        }
    }
}
