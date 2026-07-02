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

    /**
     * 保存或更新团队信息
     * <p>
     * 详细执行流程：
     * 1. 检查是否为新增操作 (id 为空)：
     *    a. 调用 {@link #restoreDeletedTeam(Team)}：若微信 OpenID 对应的历史团队已被逻辑删除，
     *       则复用并更新该删除记录的主键，将其删除标示恢复为 0 (激活)，从而绕过唯一索引冲突，整个保存逻辑提前成功返回。
     *    b. 若无可恢复的历史记录，则调用 {@link #existByWechatOpenid(String)} 进行防重校验。
     *       如果该微信 OpenID 已经绑定了某个活跃（未删除）团队，则返回 false（业务校验冲突）。
     *    c. 对全新数据，初始化默认的删除标示 is_deleted = 0（未删除状态）。
     * 2. 调用 super.saveOrUpdate(record) 执行数据库的插入或更新操作。
     * 3. 异常兜底：若在高并发场景下仍存在多个相同 wechat_openid 的请求透过校验并执行写入，
     *    由于底层数据库 `team` 表的 `wechat_openid` 设有唯一索引约束，最终会触发 {@link DuplicateKeyException}。
     *    此处进行显式捕获并返回 false，确保业务逻辑友好失败，避免抛出 500 系统错误。
     *
     * @param record 团队实体
     * @return 保存成功返回 true；微信 OpenID 重复或唯一索引冲突导致保存失败返回 false
     */
    @Override
    public boolean saveTeam(Team record) {
        // 1. 判断是否为新增操作 (主键 ID 为空)
        if (record.getId() == null) {
            // 1.1 尝试恢复历史已被逻辑删除的相同微信 OpenID 团队记录，以解决唯一键冲突并复用 ID
            if (restoreDeletedTeam(record)) {
                return true;
            }
            // 1.2 校验防重：避免同一个微信 OpenID 创建多个有效的活跃团队
            if (StringUtils.isNotBlank(record.getWechatOpenid()) && existByWechatOpenid(record.getWechatOpenid())) {
                return false;
            }
            // 1.3 默认设置删除标示为未删除 (0)
            if (record.getIsDeleted() == null) {
                record.setIsDeleted(SysConstants.IS_FALSE);
            }
        }
        try {
            // 2. 调用 MyBatis-Plus 的 saveOrUpdate 接口写库 (无 ID 则 insert，有 ID 则 update)
            super.saveOrUpdate(record);
            return true;
        } catch (DuplicateKeyException e) {
            // 3. 高并发兜底：捕获由于数据库 wechat_openid 唯一索引约束导致的键冲突异常
            return false;
        }
    }

    /**
     * 分页过滤查询未删除（激活）的团队列表
     * <p>
     * 查询逻辑：
     * 1. 提取并构造分页对象。
     * 2. 构造查询 Wrapper，对团队名称、负责人姓名、电话进行模糊查询 (like)。
     * 3. 对微信 OpenID 进行精确查询 (eq)。
     * 4. 强制限定查询 is_deleted = 0 的记录，并根据 ID 降序排列。
     *
     * @param vo 包含分页及过滤条件的 TeamVO 对象
     * @return 分页结果 PageResult
     */
    @Override
    public PageResult findPage(TeamVO vo) {
        // 1. 构造分页参数
        Page<Team> page = new Page<>(vo.getPageNum(), vo.getPageSize());
        // 2. 构造查询条件
        QueryWrapper<Team> ew = new QueryWrapper<>();
        // 模糊搜索：团队名称
        if (StringUtils.isNotBlank(vo.getTeamName())) {
            ew.like(Team.TEAM_NAME, vo.getTeamName());
        }
        // 模糊搜索：负责人姓名
        if (StringUtils.isNotBlank(vo.getPrincipalName())) {
            ew.like(Team.PRINCIPAL_NAME, vo.getPrincipalName());
        }
        // 模糊搜索：联系电话
        if (StringUtils.isNotBlank(vo.getMobile())) {
            ew.like(Team.MOBILE, vo.getMobile());
        }
        // 精确匹配：微信 OpenID
        if (StringUtils.isNotBlank(vo.getWechatOpenid())) {
            ew.eq(Team.WECHAT_OPENID, vo.getWechatOpenid());
        }
        // 过滤条件：只查询未被删除的团队记录
        ew.eq(Team.IS_DELETED, SysConstants.IS_FALSE);
        // 按 ID 降序排列
        ew.orderByDesc(Team.ID);
        // 3. 执行分页查询
        page = super.page(page, ew);
        return PageResultUtil.getPageResult(page);
    }

    /**
     * 批量逻辑删除团队
     * <p>
     * 通过构造删除属性的新实体对象，批量执行 updateByBatch，将 is_deleted 修改为 1。
     *
     * @param ids 待删除的团队主键 ID 集合
     */
    @Override
    public void logicDelete(List<Long> ids) {
        List<Team> teamList = new ArrayList<>();
        // 1. 遍历待删除主键，组装仅包含 id 和逻辑删除标示为 1 的 Team 对象
        for (Long id : ids) {
            Team team = new Team();
            team.setId(id);
            team.setIsDeleted(SysConstants.IS_TRUE); // 将状态修改为已删除
            teamList.add(team);
        }
        // 2. 批量执行更新
        super.updateBatchById(teamList);
    }

    /**
     * 根据微信 OpenID 查询未删除的活跃团队详情
     *
     * @param wechatOpenid 微信用户唯一标识 OpenID
     * @return 团队实体记录，按 ID 倒序取最新的一条，若不存在则返回 null
     */
    @Override
    public Team findByWechatOpenid(String wechatOpenid) {
        QueryWrapper<Team> ew = new QueryWrapper<>();
        ew.eq(Team.WECHAT_OPENID, wechatOpenid);
        ew.eq(Team.IS_DELETED, SysConstants.IS_FALSE); // 必须是未删除的
        ew.orderByDesc(Team.ID);
        ew.last("limit 1");
        return super.getOne(ew);
    }

    /**
     * 内部方法：判断某个微信 OpenID 是否已绑定有活跃的团队记录
     *
     * @param wechatOpenid 微信 OpenID
     * @return 存在未删除的团队返回 true；否则返回 false
     */
    private boolean existByWechatOpenid(String wechatOpenid) {
        QueryWrapper<Team> ew = new QueryWrapper<>();
        ew.eq(Team.WECHAT_OPENID, wechatOpenid);
        ew.eq(Team.IS_DELETED, SysConstants.IS_FALSE);
        return super.count(ew) > 0;
    }

    /**
     * 内部方法：尝试恢复先前已被逻辑删除的团队记录
     * <p>
     * 设计意图：
     * 数据库存在 wechat_openid 的唯一索引限制。若某个用户原有的团队被逻辑删除（is_deleted = 1 记录仍存在于表中），
     * 如果新请求直接 insert，将会因 wechat_openid 唯一约束触发报错。
     * 故在新增前，查找是否有逻辑删除的相同 wechat_openid 的团队记录：
     * 1. 查找历史被删除记录：{@link #findDeletedByWechatOpenid(String)}
     * 2. 若存在，则将新参数 record 的 ID 设为该历史记录 ID，并将其 is_deleted 重置为 0 (激活状态)。
     * 3. 调用 updateById 覆盖历史数据，达到“逻辑恢复且数据更新”的效果，巧妙避开唯一索引冲突。
     *
     * @param record 待新增的团队入参
     * @return 成功恢复并更新返回 true；若无历史删除记录可以恢复则返回 false
     */
    private boolean restoreDeletedTeam(Team record) {
        // 1. 若微信 OpenID 为空，直接返回 false（无法恢复）
        if (StringUtils.isBlank(record.getWechatOpenid())) {
            return false;
        }
        // 2. 查询是否存在已被逻辑删除的同微信 OpenID 团队记录
        Team deletedTeam = findDeletedByWechatOpenid(record.getWechatOpenid());
        if (deletedTeam == null) {
            return false; // 没有可恢复的记录
        }
        // 3. 复用历史记录的主键 ID，将删除标示重置为未删除 (0)
        record.setId(deletedTeam.getId());
        record.setIsDeleted(SysConstants.IS_FALSE);
        // 4. 更新该条记录数据
        super.updateById(record);
        return true; // 恢复成功
    }

    /**
     * 内部方法：查询被逻辑删除的历史团队记录
     *
     * @param wechatOpenid 微信 OpenID
     * @return 被逻辑删除（is_deleted = 1）的团队实体，按 ID 倒序取最新的一条
     */
    private Team findDeletedByWechatOpenid(String wechatOpenid) {
        QueryWrapper<Team> ew = new QueryWrapper<>();
        ew.eq(Team.WECHAT_OPENID, wechatOpenid);
        ew.eq(Team.IS_DELETED, SysConstants.IS_TRUE); // 只查已被逻辑删除的记录
        ew.orderByDesc(Team.ID);
        ew.last("limit 1");
        return super.getOne(ew);
    }
}
