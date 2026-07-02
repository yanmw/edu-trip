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
     * 保存或更新团队信息。
     * <p>
     * 业务防重与恢复设计说明：
     * 1. 唯一性限制：系统限制每个微信 OpenID (wechatOpenid) 只能创建一个活跃团队。
     * 2. 逻辑删除数据复用：若待新增的微信 OpenID 曾被“逻辑删除”过，则会直接“恢复且复用”那条记录，将其 ID 赋给当前入参并将 is_deleted 标示重置为 0，然后执行更新。
     * 3. 校验机制：若是全新的微信 OpenID 且当前没有对应活跃团队，则正常执行插入操作；若已存在激活状态的同微信 OpenID 团队，则返回 false（表示由于微信重复而保存失败）。
     *
     * @param record 团队实体信息
     * @return 保存成功（包括新增成功、更新成功、恢复逻辑删除记录成功）返回 true；
     *         若微信 OpenID 冲突（已存在活跃团队，或底层发生唯一索引异常）则返回 false。
     */
    boolean saveTeam(Team record);

    /**
     * 分页过滤查询未删除的团队列表。
     *
     * @param vo 包含分页参数（pageNum, pageSize）及过滤条件（teamName, principalName, mobile, wechatOpenid 等）的视图对象
     * @return 封装后的分页结果 PageResult，仅包含 is_deleted = 0 且匹配筛选条件的记录
     */
    PageResult findPage(TeamVO vo);

    /**
     * 批量逻辑删除团队记录。
     * <p>
     * 将对应 ID 的团队记录的 is_deleted 字段置为 1 (表示已删除)，保留数据不进行物理删除。
     *
     * @param ids 待逻辑删除的团队主键 ID 集合
     */
    void logicDelete(List<Long> ids);

    /**
     * 根据微信用户唯一标识 OpenID 查询处于活跃（未删除）状态的团队详情。
     *
     * @param wechatOpenid 微信用户唯一标识 OpenID
     * @return 匹配的激活团队记录；若不存在，则返回 null
     */
    Team findByWechatOpenid(String wechatOpenid);
}
