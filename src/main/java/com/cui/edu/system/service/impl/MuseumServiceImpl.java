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
import com.cui.edu.system.service.MuseumSaveResult;
import com.cui.edu.system.service.MuseumService;
import com.cui.edu.vo.system.MuseumVO;
import org.springframework.dao.DuplicateKeyException;
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
    public MuseumSaveResult saveMuseum(Museum record) {
        if (StringUtils.isNotBlank(record.getName()) && existByName(record.getName(), record.getId())) {
            return MuseumSaveResult.DUPLICATE_NAME;
        }
        if (record.getId() == null) {
            // mid为银联商户号，同一个商户号不允许重复创建博物馆。
            if (StringUtils.isNotBlank(record.getMid()) && existByMid(record.getMid())) {
                return MuseumSaveResult.DUPLICATE_MID;
            }
            if (record.getStatus() == null) {
                record.setStatus(SysConstants.IS_TRUE);
            }
            if (record.getIdCardVerifyEnabled() == null) {
                record.setIdCardVerifyEnabled(SysConstants.IS_FALSE);
            }
        }
        try {
            super.saveOrUpdate(record);
            return MuseumSaveResult.SUCCESS;
        } catch (DuplicateKeyException e) {
            // 数据库唯一约束兜底，避免并发新增时绕过前置查询。
            return MuseumSaveResult.DUPLICATE_MID;
        }
    }

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

    private boolean existByMid(String mid) {
        QueryWrapper<Museum> ew = new QueryWrapper<>();
        ew.eq(Museum.MID, mid);
        return super.count(ew) > 0;
    }

    private boolean existByName(String name, Long id) {
        QueryWrapper<Museum> ew = new QueryWrapper<>();
        ew.eq(Museum.NAME, name);
        if (id != null) {
            ew.ne(Museum.ID, id);
        }
        return super.count(ew) > 0;
    }
}
