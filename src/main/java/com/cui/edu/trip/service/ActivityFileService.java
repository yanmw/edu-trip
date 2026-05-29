package com.cui.edu.trip.service;

import com.cui.edu.trip.entity.ActivityFile;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cui.edu.common.PageResult;
import com.cui.edu.vo.trip.ActivityFileVO;

import java.util.List;

/**
 * <p>
 * 活动文件表 服务类
 * </p>
 *
 * @author Cuicui
 * @since 2026-05-29
 */
public interface ActivityFileService extends IService<ActivityFile> {

    /**
     * 分页查询未删除的活动文件。
     */
    PageResult findPage(ActivityFileVO vo);

    /**
     * 批量逻辑删除活动文件。
     */
    void logicDelete(List<Long> ids);
}
