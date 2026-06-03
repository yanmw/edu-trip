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

    @Override
    public PageResult findPage(ActivityFileVO vo) {
        Page<ActivityFile> page = new Page<>(vo.getPageNum(), vo.getPageSize());
        QueryWrapper<ActivityFile> ew = new QueryWrapper<>();
        if (vo.getActivityId() != null) {
            ew.eq(ActivityFile.ACTIVITY_ID, vo.getActivityId());
        }
        if (vo.getMuseumId() != null) {
            ew.eq(ActivityFile.MUSEUM_ID, vo.getMuseumId());
        }
        if (StringUtils.isNotBlank(vo.getFileName())) {
            ew.like(ActivityFile.FILE_NAME, vo.getFileName());
        }
        ew.eq(ActivityFile.IS_DELETED, SysConstants.IS_FALSE);
        ew.orderByDesc(ActivityFile.ID);
        page = super.page(page, ew);
        return PageResultUtil.getPageResult(page);
    }

    @Override
    public void logicDelete(List<Long> ids) {
        List<ActivityFile> activityFileList = new ArrayList<>();
        for (Long id : ids) {
            ActivityFile activityFile = new ActivityFile();
            activityFile.setId(id);
            activityFile.setIsDeleted(SysConstants.IS_TRUE);
            activityFileList.add(activityFile);
        }
        super.updateBatchById(activityFileList);
    }
}
