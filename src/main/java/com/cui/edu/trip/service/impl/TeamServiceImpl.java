package com.cui.edu.trip.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cui.edu.trip.entity.Team;
import com.cui.edu.trip.mapper.TeamMapper;
import com.cui.edu.trip.service.TeamService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cui.edu.common.PageResult;
import com.cui.edu.common.PageResultUtil;
import com.cui.edu.common.SysConstants;
import com.cui.edu.vo.trip.TeamVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 团队表 服务实现类
 * </p>
 *
 * @author Cuicui
 * @since 2026-05-29
 */
@Service
public class TeamServiceImpl extends ServiceImpl<TeamMapper, Team> implements TeamService {

    @Override
    public boolean saveTeam(Team record) {
        if (record.getId() == null) {
            // 新增时先做业务校验，避免同一个微信用户重复创建团队。
            if (StringUtils.isNotBlank(record.getWechatOpenid()) && existByWechatOpenid(record.getWechatOpenid())) {
                return false;
            }
            if (record.getIsDeleted() == null) {
                record.setIsDeleted(SysConstants.IS_FALSE);
            }
        }
        try {
            super.saveOrUpdate(record);
            return true;
        } catch (DuplicateKeyException e) {
            // 数据库已对微信openid加唯一约束，这里兜底处理并返回业务失败。
            return false;
        }
    }

    @Override
    public PageResult findPage(TeamVO vo) {
        Page<Team> page = new Page<>(vo.getPageNum(), vo.getPageSize());
        QueryWrapper<Team> ew = new QueryWrapper<>();
        if (StringUtils.isNotBlank(vo.getTeamName())) {
            ew.like(Team.TEAM_NAME, vo.getTeamName());
        }
        if (StringUtils.isNotBlank(vo.getPrincipalName())) {
            ew.like(Team.PRINCIPAL_NAME, vo.getPrincipalName());
        }
        if (StringUtils.isNotBlank(vo.getMobile())) {
            ew.like(Team.MOBILE, vo.getMobile());
        }
        if (StringUtils.isNotBlank(vo.getWechatOpenid())) {
            ew.eq(Team.WECHAT_OPENID, vo.getWechatOpenid());
        }
        ew.eq(Team.IS_DELETED, SysConstants.IS_FALSE);
        ew.orderByDesc(Team.ID);
        page = super.page(page, ew);
        return PageResultUtil.getPageResult(page);
    }

    @Override
    public void logicDelete(List<Long> ids) {
        List<Team> teamList = new ArrayList<>();
        for (Long id : ids) {
            Team team = new Team();
            team.setId(id);
            team.setIsDeleted(SysConstants.IS_TRUE);
            teamList.add(team);
        }
        super.updateBatchById(teamList);
    }

    @Override
    public Team findByWechatOpenid(String wechatOpenid) {
        QueryWrapper<Team> ew = new QueryWrapper<>();
        ew.eq(Team.WECHAT_OPENID, wechatOpenid);
        ew.eq(Team.IS_DELETED, SysConstants.IS_FALSE);
        ew.orderByDesc(Team.ID);
        ew.last("limit 1");
        return super.getOne(ew);
    }

    private boolean existByWechatOpenid(String wechatOpenid) {
        QueryWrapper<Team> ew = new QueryWrapper<>();
        ew.eq(Team.WECHAT_OPENID, wechatOpenid);
        ew.eq(Team.IS_DELETED, SysConstants.IS_FALSE);
        return super.count(ew) > 0;
    }
}
