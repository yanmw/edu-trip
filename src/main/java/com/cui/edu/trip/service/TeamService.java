package com.cui.edu.trip.service;

import com.cui.edu.trip.entity.Team;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cui.edu.common.PageResult;
import com.cui.edu.vo.trip.TeamVO;

import java.util.List;

/**
 * <p>
 * 团队表 服务类
 * </p>
 *
 * @author Cuicui
 * @since 2026-05-29
 */
public interface TeamService extends IService<Team> {

    /**
     * 保存团队；新增时微信openid重复则返回false。
     */
    boolean saveTeam(Team record);

    /**
     * 分页查询未删除的团队。
     */
    PageResult findPage(TeamVO vo);

    /**
     * 批量逻辑删除团队。
     */
    void logicDelete(List<Long> ids);

    /**
     * 根据微信openid查询未删除的团队详情。
     */
    Team findByWechatOpenid(String wechatOpenid);
}
