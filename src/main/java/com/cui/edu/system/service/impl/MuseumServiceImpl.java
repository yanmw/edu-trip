package com.cui.edu.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cui.edu.common.PageResult;
import com.cui.edu.common.PageResultUtil;
import com.cui.edu.common.SysConstants;
import com.cui.edu.system.entity.Museum;
import com.cui.edu.system.mapper.MuseumMapper;
import com.cui.edu.system.service.MuseumService;
import com.cui.edu.vo.system.MuseumVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author Cuicui
 * @since 2026-02-25
 */
@Service
public class MuseumServiceImpl extends ServiceImpl<MuseumMapper, Museum> implements MuseumService {

    @Override
    public PageResult findPage(MuseumVO vo) {
        Page<Museum> page = new Page<>(vo.getPageNum(), vo.getPageSize());
        QueryWrapper<Museum> ew = new QueryWrapper<>();
        if (StringUtils.isNotBlank(vo.getName())) {
            ew.like(Museum.NAME, vo.getName());
        }
        ew.eq(Museum.STATUS, SysConstants.IS_TRUE);
        ew.orderByDesc(Museum.ID);
        page = super.page(page, ew);
        return PageResultUtil.getPageResult(page);
    }

    @Override
    public void logicDelete(List<Long> ids) {
        List<Museum> museumList = new ArrayList<>();
        for (Long id : ids) {
            Museum museum = new Museum();
            museum.setId(id);
            museum.setStatus(SysConstants.IS_FALSE);
            museumList.add(museum);
        }
        super.updateBatchById(museumList);
    }
}
