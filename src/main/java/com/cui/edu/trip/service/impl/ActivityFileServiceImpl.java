package com.cui.edu.trip.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cui.edu.trip.entity.ActivityFile;
import com.cui.edu.trip.mapper.ActivityFileMapper;
import com.cui.edu.trip.service.ActivityFileService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cui.edu.common.PageResult;
import com.cui.edu.common.PageResultUtil;
import com.cui.edu.common.SysConstants;
import com.cui.edu.vo.trip.ActivityFileVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 活动文件表 服务实现类
 * </p>
 *
 * @author Cuicui
 * @since 2026-05-29
 */
@Service
public class ActivityFileServiceImpl extends ServiceImpl<ActivityFileMapper, ActivityFile> implements ActivityFileService {

    /**
     * 分页过滤查询活动关联的文件列表
     *
     * @param vo 包含分页信息及活动 ID、博物馆 ID、文件名称等过滤条件的 ActivityFileVO
     * @return 分页结果 PageResult
     */
    @Override
    public PageResult findPage(ActivityFileVO vo) {
        Page<ActivityFile> page = new Page<>(vo.getPageNum(), vo.getPageSize());
        QueryWrapper<ActivityFile> ew = new QueryWrapper<>();
        // 1. 精确匹配活动 ID
        if (vo.getActivityId() != null) {
            ew.eq(ActivityFile.ACTIVITY_ID, vo.getActivityId());
        }
        // 2. 精确匹配博物馆 ID
        if (vo.getMuseumId() != null) {
            ew.eq(ActivityFile.MUSEUM_ID, vo.getMuseumId());
        }
        // 3. 模糊匹配文件名称
        if (StringUtils.isNotBlank(vo.getFileName())) {
            ew.like(ActivityFile.FILE_NAME, vo.getFileName());
        }
        // 4. 仅查询未删除的有效文件
        ew.eq(ActivityFile.IS_DELETED, SysConstants.IS_FALSE);
        ew.orderByDesc(ActivityFile.ID);
        page = super.page(page, ew);
        return PageResultUtil.getPageResult(page);
    }

    /**
     * 批量逻辑删除活动文件
     *
     * @param ids 待逻辑删除的文件 ID 集合
     */
    @Override
    public void logicDelete(List<Long> ids) {
        List<ActivityFile> activityFileList = new ArrayList<>();
        // 1. 组装待删除的数据载荷
        for (Long id : ids) {
            ActivityFile activityFile = new ActivityFile();
            activityFile.setId(id);
            activityFile.setIsDeleted(SysConstants.IS_TRUE); // 状态更新为已删除
            activityFileList.add(activityFile);
        }
        // 2. 批量更新
        super.updateBatchById(activityFileList);
    }
}
