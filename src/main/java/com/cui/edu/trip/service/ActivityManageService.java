package com.cui.edu.trip.service;

import com.cui.edu.trip.entity.ActivityManage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cui.edu.common.PageResult;
import com.cui.edu.vo.trip.ActivityManageVO;

import java.util.List;

/**
 * <p>
 * 活动管理表 服务类
 * </p>
 *
 * @author Cuicui
 * @since 2026-05-29
 */
public interface ActivityManageService extends IService<ActivityManage> {

    /**
     * 保存活动及其场次；保存成功返回null，修改受限字段时返回错误提示。
     */
    String saveActivityManage(ActivityManage record);

    /**
     * 审核通过活动，将活动状态置为启用。
     */
    String auditActivity(Long id);

    /**
     * 分页查询未删除的活动。
     */
    PageResult findPage(ActivityManageVO vo);

    /**
     * 批量逻辑删除活动，并同步清理活动场次。
     */
    void logicDelete(List<Long> ids);

    /**
     * 根据ID查询活动详情，包含活动场次。
     */
    ActivityManage findById(Long id);

    /**
     * 根据博物馆ID和可选活动分类查询启用且未删除的活动列表，包含活动场次。
     */
    List<ActivityManage> findByMuseumId(Long museumId, Integer participationType);
}
